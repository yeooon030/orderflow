package com.orderflow.order.repository;

import com.orderflow.order.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문 목록 조회는 Specification으로 조립한다. (OrderSpecifications)
 *
 * JPQL에 {@code (:status is null or o.status = :status)} 형태로 선택 조건을 넣으면
 * 값이 null일 때 PostgreSQL이 파라미터 타입을 추론하지 못해 실패한다.
 * {@code ERROR: could not determine data type of parameter $4}
 */
public interface OrderRepository extends JpaRepository<Order, Long>,
        JpaSpecificationExecutor<Order> {

    /**
     * 주문 상세 조회. items를 fetch join으로 함께 읽는다.
     *
     * open-in-view가 false이므로 Controller 단계에서는 Lazy 로딩이 불가능하다.
     * fetch join이 없으면 Service에서 items를 건드릴 때 조회가 한 번 더 나간다.
     */
    @Query("select o from Order o left join fetch o.items where o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
}
