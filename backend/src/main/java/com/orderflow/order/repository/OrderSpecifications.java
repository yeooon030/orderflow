package com.orderflow.order.repository;

import com.orderflow.order.entity.Order;
import com.orderflow.order.entity.OrderStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

/**
 * 주문 목록 조회 조건. userId는 필수이고 나머지는 값이 있을 때만 조건이 된다.
 *
 * 선택 조건을 하나의 JPQL에 {@code (:param is null or ...)}로 담지 않는다.
 * 값이 null이면 PostgreSQL이 그 파라미터의 타입을 추론하지 못해 쿼리가 실패한다.
 * 조건이 없을 때 null을 반환하면 Spring Data가 해당 조건을 아예 빼고 조립하므로,
 * 실행되는 SQL에 사용하지 않는 조건이 남지 않는다.
 *
 * 이 쿼리는 Day 7~8 성능 측정 대상이다. Index는 Execution Plan을 확인한 뒤
 * 별도 Migration으로 추가한다. (BACKEND.md 14, 17)
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> ofUser(Long userId) {
        return (root, query, builder) -> builder.equal(root.get("userId"), userId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Order> createdOnOrAfter(LocalDateTime from) {
        if (from == null) {
            return null;
        }
        return (root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdBefore(LocalDateTime to) {
        if (to == null) {
            return null;
        }
        return (root, query, builder) -> builder.lessThan(root.get("createdAt"), to);
    }
}
