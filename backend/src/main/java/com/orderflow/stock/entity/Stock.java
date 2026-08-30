package com.orderflow.stock.entity;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Product와 1:1 관계이며, 별도 id 없이 productId를 PK이자 Product FK로 사용한다.
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Stock() {
    }

    public Stock(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고를 차감한다.
     *
     * 이 메서드는 재고를 검사하지만, 동시 주문에서 정합성을 보장하는 것은 검사가 아니라
     * 호출 전에 획득한 Pessimistic Lock이다. Lock 없이 호출하면 여러 트랜잭션이
     * 같은 수량을 읽고 각자 차감해 lost update가 발생한다. (BACKEND.md 8)
     *
     * 부족할 때 던지는 예외는 unchecked여야 한다. checked면 Spring 기본 롤백 규칙이
     * 적용되지 않아 재고만 차감된 채 주문이 없는 상태가 될 수 있다.
     */
    public void decrease(int amount) {
        if (quantity < amount) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        this.quantity -= amount;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
