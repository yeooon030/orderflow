package com.orderflow.stock.service;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ErrorCode;
import com.orderflow.stock.dto.StockResponse;
import com.orderflow.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * Stock의 PK가 product_id이고 Product와 1:1이 스키마로 강제되므로,
     * 재고가 없으면 상품이 없는 것으로 보고 PRODUCT_NOT_FOUND를 반환한다.
     */
    public StockResponse getStock(Long productId) {
        return stockRepository.findById(productId)
                .map(StockResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
