# 재고 차감 동시성 제어 결정

## 문제

재고 차감은 읽고, 검사하고, 쓰는 세 단계다.

```text
SELECT quantity FROM stock WHERE product_id = ?
→ quantity >= 주문 수량 인지 검사
→ UPDATE stock SET quantity = quantity - 주문 수량
```

동시에 들어온 주문이 각자 같은 값을 읽으면, 검사는 둘 다 통과하고 차감은 각자 수행한다.
나중 UPDATE가 앞선 UPDATE를 덮어써 차감 한 번이 사라진다(lost update).

재고보다 많은 주문이 성공하면 팔 수 없는 물건을 판 상태가 된다.
이 프로젝트에서 재고 정합성은 성능보다 우선한다.

## 선택지

| | 방식 | 내용 |
|---|---|---|
| A | 제어 없음 | 읽고 검사하고 쓴다. 동시성 제어 없음 |
| B | Pessimistic Lock | `SELECT ... FOR UPDATE`로 행을 잠그고 차감 |
| C | Optimistic Lock | `@Version`으로 충돌을 감지하고 재시도 |

## 비교 기준

- 동시 주문에서 재고 정합성이 유지되는가 (측정)
- 구현과 검증의 복잡도
- 프로젝트 규칙과의 정합

## 선택

**B — Pessimistic Lock (`PESSIMISTIC_WRITE`).**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Stock s where s.productId = :productId")
Optional<Stock> findByProductIdForUpdate(@Param("productId") Long productId);
```

Lock timeout은 설정하지 않고 DB 기본값을 사용한다.

## 선택 이유

### A는 실제로 깨진다

측정 결과는 아래 검증 결과에 있다. 재고 100에 대해 200개가 판매됐다.

### C는 비교하지 않았다

프로젝트 규칙이 Pessimistic Lock으로 고정되어 있고, 목표는 두 방식의 비교가 아니라
재고 정합성 보장이다. (DEVELOPMENT_RULES 8)

**따라서 이 문서는 Optimistic Lock이 더 낫다거나 못하다는 결론을 담지 않는다.**
구현하지도 측정하지도 않았으므로 그런 결론을 낼 근거가 없다.

## 검증 결과

`OrderConcurrencyTest`가 이 결정을 검증한다.

```text
초기 재고 100 / 동시 요청 100건 / 요청당 수량 2
```

### Lock 적용 상태

```text
성공 50건, 재고 부족 50건, 최종 재고 0, 생성된 주문 50건
예상 밖 예외 0건
```

### Lock 제거 상태

`@Lock(LockModeType.PESSIMISTIC_WRITE)` 한 줄만 제거하고 같은 테스트를 실행했다.

```text
expected: 50
 but was: 100
```

100건이 전부 성공했다. 재고 100에서 200개가 판매됐다.

**Lock이 없으면 코드의 재고 검사는 아무것도 막지 못한다.** 검사 자체는 그대로 실행되지만,
모든 트랜잭션이 차감 전 값을 읽으므로 전부 통과한다.

### 등식만으로는 검증되지 않는다

`Initial Stock = Final Stock + Success Quantity`는 필요조건이지만 충분조건이 아니다.
모든 요청이 실패해도 성립한다.

```text
100 = 100 + 0 × 2
```

그래서 테스트는 세 가지를 함께 단언한다.

- 성공 건수가 정확히 50인가
- 최종 재고가 0인가
- 재고 부족이 아닌 예외가 하나도 없는가

세 번째가 없으면 요청이 다른 이유로 전부 실패했을 때도 통과한다.

## 적용 방법

### Lock 조회는 트랜잭션 안에서만 호출한다

`SELECT ... FOR UPDATE`가 잡은 Lock은 트랜잭션이 끝날 때 풀린다.

`@Transactional`이 없는 곳에서 Lock 조회 메서드를 호출하면 Spring Data가 그 메서드만을 위해
트랜잭션을 열고 메서드가 끝나며 커밋한다. Lock은 조회 직후 해제되고, 이어지는 차감은
아무 보호를 받지 못한다. 코드에는 `@Lock`이 붙어 있으므로 겉보기로는 구분되지 않는다.

이 조건은 코드 검토로 확인하지 않는다. 동시성 테스트로 확인한다.

### Lock 적용 여부는 테스트를 깨뜨려서 확인한다

동시성 테스트가 통과한다는 사실만으로는 그 테스트가 Lock을 검증한다고 말할 수 없다.
Lock을 제거했을 때 실패하는지 확인해야 한다.

실패하지 않으면 동시성이 실제로 발생하지 않은 것이고, 그 테스트는 통과해도 의미가 없다.
