package com.orderflow.order.dto;

import com.orderflow.order.entity.Order;
import com.orderflow.order.entity.OrderStatus;
import java.time.LocalDateTime;

/**
 * 주문 목록 응답. items를 포함하지 않는다.
 *
 * 목록에 items를 넣으면 주문 건수만큼 OrderItem 조회가 붙는다.
 * 이 API는 Day 7~8 성능 측정 대상이므로 baseline에 N+1을 섞지 않는다.
 */
public record OrderSummaryResponse(
        Long id,
        Long userId,
        OrderStatus status,
        Long totalPrice,
        LocalDateTime createdAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getCreatedAt());
    }
}
