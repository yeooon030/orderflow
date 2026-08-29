package com.orderflow.stock.dto;

import com.orderflow.stock.entity.Stock;
import java.time.LocalDateTime;

public record StockResponse(Long productId, Integer quantity, LocalDateTime updatedAt) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(
                stock.getProductId(),
                stock.getQuantity(),
                stock.getUpdatedAt());
    }
}
