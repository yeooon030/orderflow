package com.orderflow.order.dto;

import com.orderflow.order.entity.OrderItem;

public record OrderItemResponse(Long id, Long productId, Integer quantity, Long price) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getPrice());
    }
}
