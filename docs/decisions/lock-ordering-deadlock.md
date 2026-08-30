# 여러 상품 주문 시 Lock 획득 순서 결정

## 문제

한 주문에 여러 상품이 담기면 트랜잭션 하나가 여러 재고 행을 잠근다.
요청에 담긴 순서대로 잠그면 두 요청의 잠금 순서가 서로 반대일 수 있다.

```text
트랜잭션 1:  상품 A 잠금  →  상품 B 잠금 대기
트랜잭션 2:  상품 B 잠금  →  상품 A 잠금 대기
```

서로 상대가 쥔 Lock을 기다린다. 어느 쪽도 진행하지 못한다.

PostgreSQL은 이 상태를 감지해 한쪽 트랜잭션을 강제 종료한다.
사용자에게는 재고와 무관한 실패로 나타나고, 감지까지 걸리는 시간만큼 응답이 지연된다.

Pessimistic Lock을 쓰기로 한 이상 이 상황은 구조적으로 발생할 수 있다.
([pessimistic-lock.md](pessimistic-lock.md))

## 선택지

| | 방식 | 내용 |
|---|---|---|
| A | 요청 순서대로 잠금 | 별도 처리 없음 |
| B | 잠금 순서 고정 | productId 오름차순으로 정렬한 뒤 잠금 |
| C | 재시도 | 데드락이 발생하면 트랜잭션을 다시 실행 |

## 비교 기준

- 데드락 발생 여부 (측정)
- 응답 시간 (측정)
- 구현 비용

## 선택

**B — productId 오름차순으로 정렬한 뒤 잠근다.**

```java
private List<OrderCreateRequest.Item> lockOrderOf(List<OrderCreateRequest.Item> items) {
    return items.stream()
            .sorted(Comparator.comparing(OrderCreateRequest.Item::productId))
            .toList();
}
```

## 선택 이유

### 데드락의 성립 조건 자체를 없앤다

데드락은 두 트랜잭션의 잠금 순서가 엇갈릴 때만 발생한다.
모든 트랜잭션이 같은 기준으로 정렬해 잠그면 순서가 엇갈릴 수 없다.

감지하고 대응하는 것이 아니라 발생하지 않게 만든다. 코드는 정렬 3줄이다.

### A는 실제로 데드락이 발생한다

측정 결과는 아래에 있다.

### C를 선택하지 않은 이유

재시도는 데드락이 이미 발생한 뒤의 대응이다. 감지까지 기다리는 시간과 재실행 비용이 그대로 남고,
재시도 횟수와 백오프라는 판단할 것이 늘어난다.

B로 발생 자체를 막을 수 있으므로 이 비용을 감수할 이유가 없다.

## 검증 결과

`OrderDeadlockTest`가 이 결정을 검증한다.

```text
상품 2개 / 스레드 40개 / 5라운드
절반은 [A, B] 순서로 주문, 절반은 [B, A] 순서로 주문
재고는 10,000으로 두어 재고 부족이 섞이지 않게 한다
```

판정은 예외 종류로 한다. 데드락은 `BusinessException`이 아닌 예외로 나타나므로,
예상 밖 예외 목록이 비어 있지 않으면 실패다.

### 정렬 적용 상태

```text
통과. 예상 밖 예외 0건. 실행 시간 4초
```

### 정렬 제거 상태

`lockOrderOf`가 요청 순서를 그대로 반환하도록 바꾸고 3회 실행했다.

```text
시도 1 FAILED / 시도 2 FAILED / 시도 3 FAILED
실행 시간 1분 15초 ~ 1분 22초
```

5라운드에서 `deadlock detected` 182건이 발생했다.

```text
org.springframework.dao.CannotAcquireLockException:
  ERROR: deadlock detected
  Detail: Process 2520 waits for ExclusiveLock on tuple (0,78) of relation 16416;
          blocked by process 2535.
  Process 2535 waits for ShareLock on transaction 2353; blocked by process 2531.
  Process 2531 waits for ExclusiveLock on tuple (0,77) of relation 16416;
          blocked by process 2521.
```

### 데드락은 정합성만의 문제가 아니다

같은 테스트의 실행 시간이 **4초에서 1분 15초로 늘었다.**

PostgreSQL은 Lock 대기가 `deadlock_timeout`(기본 1초)을 넘어선 뒤에야 데드락 여부를 검사한다.
데드락에 걸린 요청마다 최소 그만큼을 기다린 뒤 실패한다.

데드락은 실패 응답을 만들 뿐 아니라 그 실패를 느리게 만든다.

### 이 테스트는 간헐적이지 않다

데드락은 타이밍에 의존하므로 재현되지 않을 가능성을 우려했다.
정렬을 제거한 상태에서 3회 연속 실행해 3회 모두 실패하는 것을 확인했다.

스레드 40개와 5라운드는 이 재현성을 얻기 위한 값이다. 값을 줄이면 재현이 불안정해질 수 있다.

## 적용 방법

여러 행을 잠그는 코드를 새로 쓸 때는 잠금 순서를 고정한다.
정렬 기준은 무엇이든 상관없지만 **모든 트랜잭션이 같은 기준을 써야 한다.**

정렬 코드는 그 자체로는 효과가 드러나지 않는다. 지워도 단일 상품 테스트는 전부 통과한다.
잠금 순서를 다루는 코드에는 순서를 엇갈리게 만드는 테스트를 함께 둔다.
