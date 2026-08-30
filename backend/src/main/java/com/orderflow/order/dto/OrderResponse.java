package com.orderflow.order.dto;

import com.orderflow.order.entity.Order;
import com.orderflow.order.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 응답. 주문 생성과 주문 상세 조회에 함께 사용한다.
 */
public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        Long totalPrice,
        LocalDateTime createdAt,
        List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
