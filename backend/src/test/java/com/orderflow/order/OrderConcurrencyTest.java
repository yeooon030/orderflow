package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ErrorCode;
import com.orderflow.order.dto.OrderCreateRequest;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.service.OrderService;
import com.orderflow.product.entity.Product;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.stock.entity.Stock;
import com.orderflow.stock.repository.StockRepository;
import com.orderflow.user.entity.User;
import com.orderflow.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * [테스트 목적] 동시 주문에서 재고보다 많이 판매되지 않는지 확인
 *
 * [테스트 방식]
 *  - @Transactional을 붙이지 않는다. 붙이면 준비 데이터가 커밋되지 않아
 *    다른 스레드의 커넥션에서 상품과 재고가 아예 보이지 않는다.
 *  - 스레드마다 자기 커넥션으로 자기 트랜잭션을 열어 실제 여러 DB Connection이
 *    SELECT ... FOR UPDATE로 경합한다. (DEVELOPMENT_RULES 8, BACKEND.md 16)
 *  - CountDownLatch로 전원이 준비된 뒤 동시에 출발시킨다.
 *
 * [등식만으로는 부족한 이유]
 *  Initial = Final + Success × Quantity 는 모든 요청이 실패해도 성립한다(100 = 100 + 0).
 *  그래서 성공 건수, 최종 재고, 예상 밖 예외 없음을 함께 단언한다.
 */
@SpringBootTest
class OrderConcurrencyTest {

    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_REQUESTS = 100;
    private static final int QUANTITY_PER_REQUEST = 2;
    private static final int EXPECTED_SUCCESS = INITIAL_STOCK / QUANTITY_PER_REQUEST;
    private static final long PRODUCT_PRICE = 1_000L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("동시성 테스트 사용자")).getId();
        productId = productRepository.save(new Product("동시성 테스트 상품", PRODUCT_PRICE)).getId();
        stockRepository.save(new Stock(productId, INITIAL_STOCK));
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    void 동시_주문에서_재고보다_많이_판매되지_않는다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_REQUESTS);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        orderService.createOrder(orderRequest());
                        success.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.STOCK_INSUFFICIENT) {
                            insufficient.incrementAndGet();
                        } else {
                            unexpected.add(e);
                        }
                    } catch (Throwable t) {
                        unexpected.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        int finalStock = stockRepository.findById(productId).orElseThrow().getQuantity();

        // 실패 원인이 재고 부족이 아닌 경우를 먼저 드러낸다.
        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(EXPECTED_SUCCESS);
        assertThat(insufficient.get()).isEqualTo(CONCURRENT_REQUESTS - EXPECTED_SUCCESS);
        assertThat(finalStock).isZero();
        assertThat(finalStock + success.get() * QUANTITY_PER_REQUEST).isEqualTo(INITIAL_STOCK);
        assertThat(orderCount()).isEqualTo(EXPECTED_SUCCESS);
    }

    private OrderCreateRequest orderRequest() {
        return new OrderCreateRequest(
                userId, List.of(new OrderCreateRequest.Item(productId, QUANTITY_PER_REQUEST)));
    }

    /**
     * 이 테스트가 만든 행만 FK 순서대로 지운다.
     *
     * 조건 없이 DELETE하면 개발 DB에 쌓아둔 데이터까지 조용히 사라진다.
     * Day 6에서 SMALL 데이터를 생성해 두고 테스트를 돌리면 그대로 손실된다.
     */
    private void deleteTestData() {
        jdbcTemplate.update(
                "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)",
                userId);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM stock WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    /**
     * 전체 건수가 아니라 이 테스트가 만든 사용자의 건수를 센다.
     */
    private long orderCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ?", Long.class, userId);
    }
}
