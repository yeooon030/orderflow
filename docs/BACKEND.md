# OrderFlow Backend Specification

## 1. Architecture

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
PostgreSQL

Redis
  ↑
Product Service

Spring Batch
  ↓
PostgreSQL
```
Layered Architecture를 기본으로 사용한다.

## 2. Domain
User
 │
 └── Order
       │
       └── OrderItem
             │
             └── Product
                    │
                    └── Stock
User
Field	Type	Description
id	Long	사용자 ID
name	String	사용자 이름
createdAt	LocalDateTime	생성일

회원가입 및 인증은 구현하지 않는다.

테스트 데이터 생성 시 User 데이터를 함께 생성한다.

Product
Field	Type	Description
id	Long	상품 ID
name	String	상품명
price	Long	상품 가격
createdAt	LocalDateTime	생성일

금액은 소수점이 없는 Long으로 관리한다.

주문 당시 가격은 OrderItem에 저장한다.

Stock
Field	Type	Description
productId	Long	상품 ID
quantity	Integer	재고 수량
updatedAt	LocalDateTime	수정일

Product와 1:1 관계를 가진다.

별도의 id를 두지 않고 productId를 PK이자 Product FK로 직접 사용한다.

Order
Field	Type	Description
id	Long	주문 ID
userId	Long	사용자 ID
status	Enum	주문 상태
totalPrice	Long	총 주문 금액
createdAt	LocalDateTime	주문 생성일

Status:

PENDING
COMPLETED
FAILED
OrderItem
Field	Type	Description
id	Long	주문상품 ID
orderId	Long	주문 ID
productId	Long	상품 ID
quantity	Integer	주문 수량
price	Long	주문 당시 가격
DailyOrderStatistics
Field	Type	Description
id	Long	통계 ID
statDate	LocalDate	통계 기준일
orderCount	Long	주문 수
totalAmount	Long	총 판매 금액
createdAt	LocalDateTime	생성일
updatedAt	LocalDateTime	수정일

statDate에는 UNIQUE 제약을 적용한다.

## 3. API 규칙
날짜

ISO-8601 형식을 사용한다.

2026-08-23T10:30:00

날짜만 필요한 경우:

2026-08-23
성공 응답
{
  "status": "success",
  "data": {}
}

목록 응답:

{
  "status": "success",
  "data": [],
  "pagination": {
    "page": 0,
    "size": 20,
    "total": 1000,
    "totalPages": 50
  }
}

page는 0부터 시작한다.
에러 응답
{
  "status": "error",
  "error": {
    "code": "STOCK_INSUFFICIENT",
    "message": "재고가 부족합니다.",
    "timestamp": "2026-08-23T10:30:00"
  }
}
HTTP Status
상황	Status
정상	200
생성 성공	201
잘못된 요청	400
리소스 없음	404
지원하지 않는 메서드	405
재고 부족 / 동시성 충돌	409
지원하지 않는 Content-Type	415
서버 오류	500

## 4. Product API
목록
GET /api/products?page=0&size=20
상세
GET /api/products/{productId}

상품 상세 조회는 Redis Cache 대상이다.

## 5. Stock API
조회
GET /api/products/{productId}/stock

재고 조회에는 Redis Cache를 사용하지 않는다.

## 6. Order API
주문 생성
POST /api/orders
Content-Type: application/json

Request:

{
  "userId": 1,
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ]
}

처리 순서:

User 확인
 ↓
Product 확인
 ↓
Stock Pessimistic Lock
 ↓
재고 확인
 ↓
재고 차감
 ↓
Order 생성
 ↓
OrderItem 생성
 ↓
Transaction Commit
주문 상세
GET /api/orders/{orderId}
주문 목록
GET /api/orders?userId=1&status=COMPLETED&startDate=2026-08-01&endDate=2026-08-23&page=0&size=20

주문 목록 API를 DB 성능 테스트 대상으로 사용한다.

## 7. Transaction

주문 생성 전체를 하나의 Transaction으로 처리한다.

BEGIN
 ↓
Stock Lock
 ↓
재고 검증
 ↓
재고 차감
 ↓
Order INSERT
 ↓
OrderItem INSERT
 ↓
COMMIT

처리 중 예외가 발생하면 전체 작업을 Rollback한다.

## 8. 동시성 제어

재고 차감에는 Pessimistic Lock을 사용한다.

JPA에서는 PESSIMISTIC_WRITE를 사용한다.

SELECT ...
FROM stock
WHERE product_id = ?
FOR UPDATE

Lock timeout은 별도로 설정하지 않고 기본값을 사용한다.

### 검증

초기 재고
=
성공 주문 수량
+
최종 재고

재고보다 많은 주문이 성공해서는 안 된다.

## 9. Test Data

### 규모

SMALL  = 10K Orders
MEDIUM = 100K Orders
LARGE  = 1M Orders

### 기본 Seed

고정 Seed를 사용하여 동일한 조건의 데이터를 재생성할 수 있도록 한다.

개발/테스트 환경의 기본 데이터는 다음과 같이 구성한다.

- Seed = `12345`
- User = 100명
- Product = 100개

Product 가격 범위와 Stock 초기 수량 범위는 테스트 데이터 생성기 구현 전에 결정한다.

### 생성 방법

PostgreSQL Bulk Insert를 우선한다.

데이터 생성기는 다음 옵션을 지원한다.

--size=small
--size=medium
--size=large

개발 중에는 SMALL을 기본값으로 사용한다.

## 10. Performance Test

k6를 사용한다.

주요 테스트 대상:

GET /api/orders?userId={userId}

측정:

Average
p95
p99
RPS
Error Rate
단계
100K
 ↓
Baseline
 ↓
Execution Plan
 ↓
병목 분석
 ↓
Index / Query 개선
 ↓
재측정

최종적으로 1M 데이터에서 배포 환경 검증을 1회 수행한다.

## 11. Redis

### Cache 대상

GET /api/products/{productId}

### Key

product:{productId}

### TTL

10 minutes

### Cache Miss

Redis Miss
↓
PostgreSQL 조회
↓
Redis 저장
↓
응답

### Cache Hit

Redis Hit
↓
응답

상품 변경 기능은 구현하지 않는다.

따라서 현재 프로젝트에서는 Seed 데이터가 변경되지 않는 환경을 전제로 한다.

### 검증

다음 흐름을 로그를 통해 확인한다.

Cache Miss
→ PostgreSQL 조회
→ Redis 저장

Cache Hit
→ Redis 응답

Cache Hit 이후 DB 조회가 감소하는지 확인한다.

별도의 Cache Hit/Miss 메트릭 수집은 현재 프로젝트 범위에 포함하지 않는다.

## 12. Batch

Spring Batch를 사용한다.

### 실행

POST /internal/batch/order-statistics?date=2026-08-23

### JobParameter

date=2026-08-23

### 멱등성

멱등성은 Skip 방식으로 처리한다.

동일 날짜에 SUCCESS JobExecution이 존재하면 통계를 다시 생성하지 않고 Skip한다.

DailyOrderStatistics.statDate에 UNIQUE 제약을 둔다.

UNIQUE 제약은 중복 생성을 막는 최종 안전장치이며, UPSERT는 사용하지 않는다.

동일 날짜에 대한 통계가 중복 생성되지 않아야 한다.

### Job Status

Spring Batch의 JobExecution 상태를 기준으로 처리한다.

SUCCESS
→ 기존 통계를 유지하고 Skip한다.

FAILED
→ 재실행 가능하도록 한다.

### 검증

동일 날짜로 Batch를 여러 번 실행한다.

SUCCESS JobExecution 존재
→ Skip

FAILED JobExecution
→ 재실행

최종적으로 동일 날짜의 통계 데이터가 중복 생성되지 않아야 한다.

## 13. Test Strategy
Unit Test
주문 금액 계산
주문 검증
재고 부족
상태 변경
Integration Test

Testcontainers PostgreSQL을 사용한다.

검증:

주문 생성
재고 차감
Transaction Rollback
주문 조회
Concurrency Test

실제 DB Connection을 사용하는 여러 Thread/Task로 동시 요청을 발생시킨다.

예:

Initial Stock = 100

Concurrent Requests = 100
Request Quantity = 2

Expected:
Success Orders × 2 + Final Stock = 100
Test Data

기능 테스트는 SMALL 데이터 이하를 사용한다.

개발/테스트 환경의 기본 Seed는 `12345`를 사용한다.

기본 Seed 데이터는 다음과 같다.

- User: 100명
- Product: 100개

Product 가격 범위와 Stock 초기 수량 범위는 테스트 데이터 생성기 구현 전에 결정한다.

대량 데이터 성능 테스트는 일반 통합 테스트와 분리한다.

## 14. Database Index

Index는 실제 쿼리 패턴과 Execution Plan을 분석한 후 추가한다.

주문 조회 성능 개선 과정에서 필요한 Index를 검토한다.

무분별하게 Index를 추가하지 않는다.

## 15. Error Handling

전역 예외 처리를 사용한다.

Error Code는 도메인 오류와 요청 형식 오류로 나눈다.

도메인 오류:

USER_NOT_FOUND
PRODUCT_NOT_FOUND
ORDER_NOT_FOUND
INVALID_ORDER_QUANTITY
STOCK_INSUFFICIENT

요청 형식 오류:

INVALID_REQUEST
ENDPOINT_NOT_FOUND
METHOD_NOT_ALLOWED
UNSUPPORTED_MEDIA_TYPE

값이 오지 않은 것과 값이 잘못된 것을 구분한다.

userId나 productId가 요청에 없는 경우는 `INVALID_REQUEST`를 사용한다.
`*_NOT_FOUND`는 실제로 존재하지 않는 id를 보낸 경우에만 사용한다.

`INVALID_ORDER_QUANTITY`는 수량 값 자체가 잘못된 경우에만 사용한다.
주문 항목이 비어 있는 경우는 수량 문제가 아니므로 `INVALID_REQUEST`를 사용한다.

예외 응답은 공통 Error Response 형식을 사용한다.

전역 예외 처리는 Spring MVC의 표준 예외를 넘겨받아야 한다.

`@ExceptionHandler(Exception.class)`만 두면 파라미터 누락, 본문 파싱 실패, 타입 불일치 등
클라이언트 입력 오류가 Spring의 변환을 거치기 전에 매칭되어 전부 500으로 응답된다.

클라이언트 입력 오류에는 ERROR 로그를 남기지 않는다.

## 16. Batch / Performance / Concurrency 원칙
Batch

대량 데이터를 한 번에 메모리에 적재하지 않는다.

Chunk 기반 처리를 사용한다.

Performance

측정하지 않은 성능 수치를 문서에 작성하지 않는다.

Concurrency

동시성 문제는 단위 테스트만으로 검증하지 않는다.

실제 여러 DB Connection을 사용하는 통합 테스트로 검증한다.

## 17. Database Migration

PostgreSQL 스키마 변경은 Flyway를 사용하여 관리한다.

Migration 파일을 통해 데이터베이스 스키마 변경 이력을 관리한다.

개발, 테스트, 운영 환경에서 동일한 Migration 기준을 사용한다.

테스트 환경에서는 Testcontainers PostgreSQL을 사용하며 Flyway Migration을 적용한다.

Hibernate의 Entity 기반 자동 스키마 변경에 의존하지 않는다.

Index 추가 및 변경 역시 실제 Query와 Execution Plan을 분석한 후 Migration으로 관리한다.

## 18. Database 규칙

### 명명 규칙

테이블명과 컬럼명은 snake_case를 사용한다.

테이블명은 단수형을 기본으로 하고, SQL 예약어와 충돌하는 경우에만 복수형을 사용한다.

`order`와 `user`는 PostgreSQL 예약어이므로
주문 테이블명은 `orders`, 사용자 테이블명은 `users`를 사용한다.

Entity 이름은 도메인 모델 기준(`Order`, `User`)을 유지하고
테이블 매핑으로 위 이름을 지정한다.

### PK 생성 전략

PK는 Sequence 전략을 사용한다.

Sequence는 테이블별로 `<테이블명>_seq`로 생성한다.

Sequence의 `INCREMENT BY`와 JPA의 `allocationSize`는 반드시 동일한 값을 사용한다.

값이 다르면 Hibernate가 기동 시점에 예외를 던지고 애플리케이션이 뜨지 않는다.
따라서 스키마와 Entity 중 한쪽만 변경해서는 안 된다.

현재 값은 `50`을 사용한다. 50인 이유와 측정 결과는
`docs/decisions/sequence-allocation-size.md`에 기록한다.

pooled 할당을 사용하므로 PK는 연속하지 않는다. ID를 순번이나 건수로 해석하지 않는다.

### Stock PK

Stock은 별도의 id를 두지 않고 productId를 PK이자 Product FK로 직접 사용하여
Product와 Stock의 1:1 관계를 스키마 수준에서 강제한다.

## 19. 로컬 개발 환경

로컬 개발 환경은 Docker Compose로 구성한다.

Compose 파일은 프로젝트 루트에 `docker-compose.yml`로 둔다.

### 버전 및 포트

| 구성 | 버전 | 이미지 | 포트 |
|---|---|---|---|
| PostgreSQL | 17 | `postgres:17-alpine` | 5432 |
| Redis | 7.4 | `redis:7.4-alpine` | 6379 |
| Application | — | — | 8080 |

### 접속 정보

로컬 전용 값이며 배포 환경에서는 환경변수를 사용한다.

- Database: `orderflow`
- User / Password: `orderflow` / `orderflow`

### 버전 선택 기준

로컬, Testcontainers, 배포 환경의 PostgreSQL 버전을 일치시킨다.

버전이 다르면 Execution Plan 분석 결과와 Migration 기준이 달라질 수 있다.

AWS RDS 및 ElastiCache가 제공하는 버전은 배포 단계에서 확인한 뒤
위 버전과 다를 경우 로컬 버전을 맞춘다.

### 스키마 관리

Hibernate `ddl-auto`는 `validate`를 사용한다.

스키마 생성과 변경은 Flyway Migration으로만 수행한다.
