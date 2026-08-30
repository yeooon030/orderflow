package com.orderflow.order.dto;

import java.util.List;

/**
 * 주문 생성 요청. (BACKEND.md 6)
 *
 * 값 검증은 Bean Validation이 아니라 OrderService에서 수행한다.
 * 검증 실패를 BACKEND.md 15의 Error Code로 그대로 돌려주기 위해서다.
 */
public record OrderCreateRequest(Long userId, List<Item> items) {

    public record Item(Long productId, Integer quantity) {
    }
}
