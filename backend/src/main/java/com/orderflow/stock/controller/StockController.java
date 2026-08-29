package com.orderflow.stock.controller;

import com.orderflow.common.response.ApiResponse;
import com.orderflow.stock.dto.StockResponse;
import com.orderflow.stock.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/api/products/{productId}/stock")
    public ApiResponse<StockResponse> getStock(@PathVariable Long productId) {
        return ApiResponse.success(stockService.getStock(productId));
    }
}
