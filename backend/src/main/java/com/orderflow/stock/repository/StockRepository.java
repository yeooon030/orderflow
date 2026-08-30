package com.orderflow.stock.repository;

import com.orderflow.stock.entity.Stock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * 재고를 잠그고 조회한다. {@code SELECT ... FOR UPDATE}로 실행된다.
     *
     * Lock은 트랜잭션 커밋 시점에 풀린다. 따라서 이 메서드는 반드시 호출부의
     * 트랜잭션 안에서 호출해야 한다. 트랜잭션 밖에서 호출하면 Spring Data가 연 트랜잭션이
     * 메서드 종료와 함께 커밋되면서 Lock이 즉시 해제되어 아무것도 막지 못한다.
     *
     * Lock timeout은 설정하지 않고 DB 기본값을 사용한다. (BACKEND.md 8)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.productId = :productId")
    Optional<Stock> findByProductIdForUpdate(@Param("productId") Long productId);
}
