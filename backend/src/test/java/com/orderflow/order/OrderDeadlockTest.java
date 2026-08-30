package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.order.dto.OrderCreateRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * [테스트 목적] 두 상품을 서로 반대 순서로 주문해도 데드락이 발생하지 않는지 확인
 *
 * [막으려는 상황]
 *  트랜잭션 1: 상품 A 잠금 → 상품 B 잠금 대기
 *  트랜잭션 2: 상품 B 잠금 → 상품 A 잠금 대기
 *  서로 상대가 쥔 Lock을 기다린다. PostgreSQL이 감지해 한쪽을 강제 종료하고
 *  SQLState 40P01을 던진다.
 *
 *  OrderService.lockOrderOf()가 요청 순서와 무관하게 productId 오름차순으로 잠그므로
 *  모든 트랜잭션의 잠금 순서가 같아져 이 교차가 성립하지 않는다.
 *
 * [테스트 방식]
 *  절반은 [A, B] 순서로, 절반은 [B, A] 순서로 동시에 주문한다.
 *  재고를 넉넉히 두어 STOCK_INSUFFICIENT가 섞이지 않게 한다.
 *  데드락은 타이밍에 의존하므로 여러 라운드를 반복한다.
 *
 * [무엇으로 실패를 판정하는가]
 *  데드락은 BusinessException이 아닌 예외(CannotAcquireLockException)로 나타난다.
 *  unexpected 목록이 비어 있지 않으면 실패다.
 */
@SpringBootTest
class OrderDeadlockTest {

    private static final int THREADS = 40;
    private static final int ROUNDS = 5;
    private static final int INITIAL_STOCK = 10_000;
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
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long productAId;
    private Long productBId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("데드락 테스트 사용자")).getId();
        productAId = productRepository.save(new Product("데드락 상품 A", PRODUCT_PRICE)).getId();
        productBId = productRepository.save(new Product("데드락 상품 B", PRODUCT_PRICE)).getId();
        stockRepository.save(new Stock(productAId, INITIAL_STOCK));
        stockRepository.save(new Stock(productBId, INITIAL_STOCK));
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    void 두_상품을_반대_순서로_주문해도_데드락이_발생하지_않는다() throws Exception {
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (int round = 0; round < ROUNDS; round++) {
            runOneRound(unexpected);
        }

        assertThat(unexpected).isEmpty();
    }

    private void runOneRound(List<Throwable> unexpected) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        try {
            for (int i = 0; i < THREADS; i++) {
                boolean forward = i % 2 == 0;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        orderService.createOrder(forward ? requestAThenB() : requestBThenA());
                    } catch (BusinessException e) {
                        unexpected.add(e);
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
    }

    private OrderCreateRequest requestAThenB() {
        return new OrderCreateRequest(userId, List.of(
                new OrderCreateRequest.Item(productAId, 1),
                new OrderCreateRequest.Item(productBId, 1)));
    }

    private OrderCreateRequest requestBThenA() {
        return new OrderCreateRequest(userId, List.of(
                new OrderCreateRequest.Item(productBId, 1),
                new OrderCreateRequest.Item(productAId, 1)));
    }

    private void deleteTestData() {
        jdbcTemplate.update(
                "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)",
                userId);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM stock WHERE product_id IN (?, ?)", productAId, productBId);
        jdbcTemplate.update("DELETE FROM product WHERE id IN (?, ?)", productAId, productBId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }
}
