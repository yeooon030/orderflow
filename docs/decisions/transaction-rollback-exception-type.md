# Transaction 롤백과 예외 타입 결정

## 문제

주문 생성은 재고 차감과 주문 저장을 하나의 트랜잭션으로 처리한다.

```text
BEGIN → Stock Lock → 재고 검증 → 재고 차감 → Order INSERT → OrderItem INSERT → COMMIT
```

중간에 실패하면 전체가 롤백되어야 한다. 재고만 차감되고 주문이 없는 상태는 재고 유실이다.

그런데 Spring의 선언적 트랜잭션은 **모든 예외에 대해 롤백하지 않는다.**
기본 롤백 규칙은 unchecked 예외(`RuntimeException`, `Error`)에만 적용된다.

재고 부족처럼 정상 흐름에서 발생하는 실패를 어떤 예외로 표현할지에 따라
롤백 여부가 달라진다.

## 선택지

| | 방식 | 내용 |
|---|---|---|
| A | checked 예외 | `extends Exception` |
| B | unchecked 예외 | `extends RuntimeException` |
| C | checked + `rollbackFor` | checked로 두고 `@Transactional(rollbackFor = ...)` 지정 |

## 비교 기준

- 기본 설정에서 롤백되는가 (측정)
- 설정 누락 시 어떤 상태가 되는가
- 호출부에 강제되는 처리

## 선택

**B — `BusinessException extends RuntimeException`.**

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    ...
}
```

## 선택 이유

### A는 롤백되지 않는다

측정 결과는 아래에 있다. 재고 차감이 커밋된 채로 남는다.

### C를 선택하지 않은 이유

`rollbackFor`를 지정하면 checked 예외도 롤백된다. 동작은 맞다.

문제는 **누락이 조용하다**는 점이다. 새로 추가한 `@Transactional` 메서드에 `rollbackFor`를
빠뜨리면 그 메서드만 롤백되지 않는다. 컴파일 오류도 테스트 실패도 없이,
재고만 차감된 데이터가 남는다.

B는 예외 타입 자체가 롤백 대상이므로 트랜잭션 메서드마다 챙길 것이 없다.

### 호출부에 처리를 강제하지 않는다

재고 부족은 Controller까지 그대로 올라가 `GlobalExceptionHandler`가 409로 변환한다.
중간 계층은 이 예외를 다룰 일이 없다. checked면 중간 계층이 전부
`throws`를 선언하거나 잡아서 다시 던져야 한다.

## 검증 결과

### checked와 unchecked의 차이를 실측했다

`@Transactional` 메서드 안에서 재고를 1 차감한 뒤 예외를 던지고,
트랜잭션 종료 후 DB에서 재고를 다시 읽었다.

```text
초기 재고 10

unchecked 예외를 던진 뒤  재고 = 10     ← 롤백됨
checked   예외를 던진 뒤  재고 = 9      ← 차감이 커밋됨
```

checked 예외에서는 예외가 발생했는데도 재고 차감이 그대로 남았다.
주문 없이 재고만 줄어든 상태다.

### 주문 전체 흐름에서도 확인했다

`OrderRollbackTest`가 이 결정을 검증한다.

```text
상품 A(재고 10) + 상품 B(재고 0) 을 한 주문으로 요청
→ STOCK_INSUFFICIENT
→ 상품 A의 재고 10 그대로
→ orders 0행, order_item 0행
```

### 메모리가 아니라 DB 롤백을 확인한 것이다

상품 A의 차감이 DB에 도달하기 전에 예외가 났다면, 이 테스트는 롤백을 검증하지 못한다.
Hibernate SQL 로그로 실행 순서를 확인했다.

```text
select ... where s1_0.product_id=? for no key update   ← 상품 A 잠금
select ...                                             ← 상품 B 조회
update stock set quantity=?, updated_at=?              ← 상품 A 차감이 DB로 나감
select ... where s1_0.product_id=? for no key update   ← 상품 B 잠금
```

상품 B를 잠그는 JPQL 조회 직전에 auto-flush가 일어나 상품 A의 `UPDATE`가 먼저 실행된다.
그 뒤에 예외가 발생하므로, 이 테스트는 실제 DB 롤백을 확인한다.

### 이 검증은 @Transactional 테스트에서는 성립하지 않는다

테스트에 `@Transactional`을 붙이면 Service의 `@Transactional`이 테스트 트랜잭션에
참여한다. 예외가 나도 실제 롤백이 아니라 rollback-only 표시만 된다.

영속성 컨텍스트도 공유하므로, 이후 조회는 DB가 아니라 1차 캐시의 엔티티를 돌려준다.
차감된 값이 그대로 보이므로 롤백 여부를 판정할 수 없다.

**롤백 검증은 트랜잭션 없는 테스트에서 한다.** 커밋된 데이터는 직접 정리한다.

## 적용 방법

- 트랜잭션 안에서 흐름을 중단시키는 예외는 unchecked로 만든다
- `@Transactional`에 `rollbackFor`를 붙여 해결하지 않는다. 누락이 드러나지 않는다
- 롤백을 검증하는 테스트에는 `@Transactional`을 붙이지 않는다
