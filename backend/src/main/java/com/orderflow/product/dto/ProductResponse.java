package com.orderflow.product.dto;

import com.orderflow.product.entity.Product;
import java.time.LocalDateTime;

public record ProductResponse(Long id, String name, Long price, LocalDateTime createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCreatedAt());
    }
}
