package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ErrorCode;
import com.orderflow.order.dto.OrderCreateRequest;
import com.orderflow.order.repository.OrderItemRepository;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.service.OrderService;
import com.orderflow.product.entity.Product;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.stock.entity.Stock;
import com.orderflow.stock.repository.StockRepository;
import com.orderflow.user.entity.User;
import com.orderflow.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * [테스트 목적] 주문 도중 예외가 발생하면 이미 차감한 재고까지 전부 롤백되는지 확인
 *
 * [테스트 방식]
 *  - @Transactional을 붙이지 않는다. 테스트가 트랜잭션을 열고 있으면
 *    Service의 @Transactional이 거기에 참여해 실제 롤백이 아니라 rollback-only 표시만 되고,
 *    영속성 컨텍스트도 공유되어 DB 상태를 판정할 수 없다.
 *  - 커밋된 데이터가 남으므로 @BeforeEach / @AfterEach에서 직접 정리한다.
 *
 * [검증이 성립하는 조건]
 *  상품 A의 재고 차감은 상품 B의 Lock 조회(JPQL) 직전에 auto-flush로 DB에 UPDATE로 나간다.
 *  따라서 이 테스트는 메모리 상태가 아니라 실제 DB 롤백을 확인한다.
 */
@SpringBootTest
class OrderRollbackTest {

    private static final long PRODUCT_PRICE = 1_000L;
    private static final int STOCK_A = 10;
    private static final int STOCK_B = 0;

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
    private OrderItemRepository orderItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long productAId;
    private Long productBId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("롤백 테스트 사용자")).getId();
        productAId = productRepository.save(new Product("재고 있는 상품", PRODUCT_PRICE)).getId();
        productBId = productRepository.save(new Product("재고 없는 상품", PRODUCT_PRICE)).getId();
        stockRepository.save(new Stock(productAId, STOCK_A));
        stockRepository.save(new Stock(productBId, STOCK_B));
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    void 뒤_상품의_재고가_부족하면_앞_상품의_차감도_롤백된다() {
        OrderCreateRequest request = new OrderCreateRequest(
                userId,
                List.of(
                        new OrderCreateRequest.Item(productAId, 1),
                        new OrderCreateRequest.Item(productBId, 1)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);

        assertThat(stockRepository.findById(productAId).orElseThrow().getQuantity())
                .isEqualTo(STOCK_A);
        assertThat(stockRepository.findById(productBId).orElseThrow().getQuantity())
                .isEqualTo(STOCK_B);
        assertThat(orderCount()).isZero();
        assertThat(orderItemCount()).isZero();
    }

    @Test
    void 주문이_성공하면_재고_차감과_주문이_함께_커밋된다() {
        OrderCreateRequest request = new OrderCreateRequest(
                userId, List.of(new OrderCreateRequest.Item(productAId, 4)));

        orderService.createOrder(request);

        assertThat(stockRepository.findById(productAId).orElseThrow().getQuantity())
                .isEqualTo(STOCK_A - 4);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(orderItemCount()).isEqualTo(1);
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
        jdbcTemplate.update("DELETE FROM stock WHERE product_id IN (?, ?)", productAId, productBId);
        jdbcTemplate.update("DELETE FROM product WHERE id IN (?, ?)", productAId, productBId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    /**
     * 전체 건수가 아니라 이 테스트가 만든 사용자의 건수를 센다.
     * 전체를 세면 개발 DB에 다른 데이터가 있을 때 단언이 깨진다.
     */
    private long orderCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ?", Long.class, userId);
    }

    private long orderItemCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_item WHERE order_id IN "
                        + "(SELECT id FROM orders WHERE user_id = ?)",
                Long.class, userId);
    }
}
