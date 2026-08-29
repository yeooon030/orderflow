package com.orderflow;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.orderflow.order.repository.OrderItemRepository;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.stock.repository.StockRepository;
import com.orderflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;

/**
 * [테스트 목적] Repository 5개가 빈으로 등록되고, 각자 매핑된 테이블에 질의할 수 있는지 확인
 *
 * [테스트 방식]
 *  - 조회 결과가 아니라 Entity-테이블 매핑과 빈 등록만 검증하므로 데이터를 넣지 않는다.
 *  - 실제 영속화 검증은 Day 6 통합 테스트에서 수행한다.
 *
 * [테스트 문법]
 * 1. assertThatCode(() -> {
 *          ...
 *    })).doesNotThrowAnyException();
 *    : 예외 발생 여부 확인
 *
 * 2. repository.count()
 *    : DB 커넥션으로 'SELECT COUNT(*) FROM 테이블명;'을 실행.
 *      @Entity와 테이블이 정상적으로 매핑되는지 확인
 */
@SpringBootTest
class RepositoryRegistrationTest {

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

    @Test
    void 모든_Repository가_매핑된_테이블에_질의할_수_있다() {
        assertThatCode(() -> {
            count(userRepository);
            count(productRepository);
            count(stockRepository);
            count(orderRepository);
            count(orderItemRepository);
        }).doesNotThrowAnyException();
    }

    private void count(CrudRepository<?, ?> repository) {
        repository.count();
    }
}
