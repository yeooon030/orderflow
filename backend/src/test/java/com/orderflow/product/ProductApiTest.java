package com.orderflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.product.entity.Product;
import com.orderflow.product.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * [테스트 목적] 상품 조회 API가 의도된 응답 형식과 HTTP Status로 응답하는지 확인
 *
 * [테스트 방식]
 *  - MockMvc로 Controller부터 DB까지 실제 흐름을 태운다.
 *  - @Transactional로 각 테스트의 데이터를 롤백하므로 테스트 간 간섭이 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 상품_목록은_pagination을_포함한_형식으로_응답한다() throws Exception {
        productRepository.save(new Product("상품A", 1000L));

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.total").exists())
                .andExpect(jsonPath("$.pagination.totalPages").exists());
    }

    @Test
    void 상품_목록은_id_오름차순으로_정렬된다() throws Exception {
        productRepository.save(new Product("상품A", 1000L));
        productRepository.save(new Product("상품B", 2000L));
        productRepository.save(new Product("상품C", 3000L));

        String body = mockMvc.perform(get("/api/products").param("size", "1000"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(body).get("data");
        List<Long> ids = new ArrayList<>();
        data.forEach(node -> ids.add(node.get("id").asLong()));

        assertThat(ids).hasSizeGreaterThanOrEqualTo(3);
        assertThat(ids).isSorted();
    }

    @Test
    void 상품_상세는_pagination_없이_응답한다() throws Exception {
        Product product = productRepository.save(new Product("상품A", 1000L));

        mockMvc.perform(get("/api/products/{productId}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(product.getId()))
                .andExpect(jsonPath("$.data.name").value("상품A"))
                .andExpect(jsonPath("$.data.price").value(1000))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void 없는_상품을_조회하면_404와_에러_응답_형식을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/{productId}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("상품을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }
}
