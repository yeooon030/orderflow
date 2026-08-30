package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.order.dto.OrderCreateRequest;
import com.orderflow.order.entity.Order;
import com.orderflow.order.entity.OrderStatus;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.product.entity.Product;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.stock.entity.Stock;
import com.orderflow.stock.repository.StockRepository;
import com.orderflow.user.entity.User;
import com.orderflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * [테스트 목적] 주문 API 3개가 의도된 응답 형식과 HTTP Status로 응답하는지 확인
 *
 * [테스트 방식]
 *  - MockMvc로 Controller부터 DB까지 실제 흐름을 태운다.
 *  - @Transactional로 각 테스트의 데이터를 롤백하므로 테스트 간 간섭이 없다.
 *
 * [이 테스트가 검증하지 않는 것]
 *  - Transaction Rollback: 테스트가 트랜잭션을 열고 있으면 Service의 @Transactional이
 *    여기에 참여하므로 실제 롤백이 아니라 rollback-only 표시만 된다.
 *    영속성 컨텍스트도 공유하므로 롤백 여부를 판정할 수 없다. → OrderRollbackTest
 *  - Pessimistic Lock: 단일 스레드에서는 경합이 발생하지 않는다. → OrderConcurrencyTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderApiTest {

    private static final long PRODUCT_PRICE = 1_000L;
    private static final int INITIAL_STOCK = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("주문 테스트 사용자")).getId();
        productId = productRepository.save(new Product("주문 테스트 상품", PRODUCT_PRICE)).getId();
        stockRepository.save(new Stock(productId, INITIAL_STOCK));
    }

    @Test
    void 주문을_생성하면_201과_주문_상세를_응답한다() throws Exception {
        mockMvc.perform(postOrder(request(productId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalPrice").value(PRODUCT_PRICE * 2))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(productId))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].price").value(PRODUCT_PRICE))
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    /**
     * flush/clear로 영속성 컨텍스트를 비운 뒤 다시 읽는다.
     * 그러지 않으면 Service가 메모리에서 바꾼 엔티티를 그대로 돌려받아
     * 실제로 UPDATE가 나갔는지 알 수 없다.
     */
    @Test
    void 주문하면_재고가_주문_수량만큼_차감된다() throws Exception {
        mockMvc.perform(postOrder(request(productId, 3)))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        Stock stock = stockRepository.findById(productId).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(INITIAL_STOCK - 3);
    }

    @Test
    void 재고보다_많이_주문하면_409를_반환한다() throws Exception {
        mockMvc.perform(postOrder(request(productId, INITIAL_STOCK + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error.code").value("STOCK_INSUFFICIENT"))
                .andExpect(jsonPath("$.error.message").value("재고가 부족합니다."))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    void 없는_사용자로_주문하면_404를_반환한다() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                999_999_999L, List.of(new OrderCreateRequest.Item(productId, 1)));

        mockMvc.perform(postOrder(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 없는_상품을_주문하면_404를_반환한다() throws Exception {
        mockMvc.perform(postOrder(request(999_999_999L, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 주문_수량이_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(postOrder(request(productId, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ORDER_QUANTITY"));
    }

    /**
     * 항목이 없는 것은 수량 값 문제가 아니라 요청이 덜 온 것이므로 INVALID_REQUEST다.
     * INVALID_ORDER_QUANTITY는 수량 값 자체가 잘못된 경우에만 쓴다.
     */
    @Test
    void 주문_항목이_비어_있으면_400_INVALID_REQUEST를_반환한다() throws Exception {
        mockMvc.perform(postOrder(new OrderCreateRequest(userId, List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void userId가_없으면_400_INVALID_REQUEST를_반환한다() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                null, List.of(new OrderCreateRequest.Item(productId, 1)));

        mockMvc.perform(postOrder(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void productId가_없으면_400_INVALID_REQUEST를_반환한다() throws Exception {
        mockMvc.perform(postOrder(request(null, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 주문_상세를_조회한다() throws Exception {
        Order order = orderRepository.save(newOrder(OrderStatus.COMPLETED));

        mockMvc.perform(get("/api/orders/{orderId}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(order.getId()))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void 없는_주문을_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 주문_목록은_pagination을_포함하고_items를_포함하지_않는다() throws Exception {
        orderRepository.save(newOrder(OrderStatus.COMPLETED));

        mockMvc.perform(get("/api/orders").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].items").doesNotExist())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1));
    }

    @Test
    void 주문_목록은_다른_사용자의_주문을_포함하지_않는다() throws Exception {
        Long otherUserId = userRepository.save(new User("다른 사용자")).getId();
        orderRepository.save(newOrder(OrderStatus.COMPLETED));
        orderRepository.save(new Order(otherUserId, OrderStatus.COMPLETED, PRODUCT_PRICE));

        mockMvc.perform(get("/api/orders").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(userId));
    }

    /**
     * 정렬 기준은 createdAt DESC, id DESC다.
     * 단일 스레드로 순차 저장하면 나중에 저장한 주문이 createdAt도 id도 크므로,
     * 두 기준 중 무엇이 적용되든 저장 역순으로 나와야 한다.
     */
    @Test
    void 주문_목록은_최신순으로_정렬된다() throws Exception {
        Long first = orderRepository.save(newOrder(OrderStatus.COMPLETED)).getId();
        Long second = orderRepository.save(newOrder(OrderStatus.COMPLETED)).getId();
        Long third = orderRepository.save(newOrder(OrderStatus.COMPLETED)).getId();
        entityManager.flush();

        mockMvc.perform(get("/api/orders").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(third))
                .andExpect(jsonPath("$.data[1].id").value(second))
                .andExpect(jsonPath("$.data[2].id").value(first));
    }

    @Test
    void 주문_목록을_status로_거른다() throws Exception {
        orderRepository.save(newOrder(OrderStatus.COMPLETED));
        orderRepository.save(newOrder(OrderStatus.PENDING));

        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(userId))
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
    }

    @Test
    void 주문_목록을_기간으로_거른다() throws Exception {
        orderRepository.save(newOrder(OrderStatus.COMPLETED));
        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(userId))
                        .param("startDate", today.toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/orders")
                        .param("userId", String.valueOf(userId))
                        .param("startDate", today.minusDays(10).toString())
                        .param("endDate", today.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private Order newOrder(OrderStatus status) {
        return new Order(userId, status, PRODUCT_PRICE);
    }

    private OrderCreateRequest request(Long productId, int quantity) {
        return new OrderCreateRequest(
                userId, List.of(new OrderCreateRequest.Item(productId, quantity)));
    }

    private MockHttpServletRequestBuilder postOrder(OrderCreateRequest request) throws Exception {
        return post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }
}
