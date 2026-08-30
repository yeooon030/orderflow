package com.orderflow.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * [테스트 목적] 클라이언트 입력 오류가 500이 아니라 알맞은 4xx로 응답되는지 확인
 *
 * [배경]
 *  GlobalExceptionHandler에 @ExceptionHandler(Exception.class)만 있던 동안에는
 *  아래 8가지가 전부 500 INTERNAL_ERROR로 나갔다. Spring MVC의 표준 예외가
 *  400/404/405/415로 변환되기 전에 catch-all에 먼저 잡혔기 때문이다.
 *  ResponseEntityExceptionHandler 상속으로 해결했고, 이 테스트가 그 회귀를 막는다.
 *
 * [테스트 방식]
 *  데이터를 넣지 않는다. 요청이 Controller의 도메인 로직에 닿기 전에 걸러지는 것들이라
 *  DB 상태와 무관하게 결과가 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RequestErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 본문이_깨진_JSON이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청이 올바르지 않습니다."))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    void 본문이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 본문의_타입이_맞지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 필수_파라미터가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 파라미터_형식이_맞지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/orders").param("userId", "1").param("status", "WRONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/orders").param("userId", "1").param("startDate", "2026/08/30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/orders/{orderId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 지원하지_않는_메서드면_405를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 지원하지_않는_ContentType이면_415를_반환한다() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void 없는_경로면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"));
    }
}
