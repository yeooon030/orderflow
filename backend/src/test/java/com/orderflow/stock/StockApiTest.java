package com.orderflow.stock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.product.entity.Product;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.stock.entity.Stock;
import com.orderflow.stock.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * [테스트 목적] 재고 조회 API가 의도된 응답 형식과 HTTP Status로 응답하는지 확인
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Test
    void 재고를_조회한다() throws Exception {
        Product product = productRepository.save(new Product("상품A", 1000L));
        stockRepository.save(new Stock(product.getId(), 100));

        mockMvc.perform(get("/api/products/{productId}/stock", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void 없는_상품의_재고를_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/stock", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }
}
