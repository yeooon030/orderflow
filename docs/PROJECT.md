# OrderFlow

## 1. 프로젝트 개요

OrderFlow는 주문과 재고를 관리하는 Spring Boot 기반 백엔드 프로젝트다.

단순 CRUD 구현을 넘어 다음과 같은 실무 상황을 경험하고 검증하는 것을 목표로 한다.

- 주문 및 재고 데이터 정합성
- 동시성 제어
- 대량 데이터 처리
- 데이터베이스 성능 분석 및 개선
- Redis 캐싱
- Batch 처리
- 테스트 자동화
- 실제 배포 환경에서의 성능 검증
- Claude Code를 활용한 개발 및 문제 해결

기능의 수보다 핵심 문제를 직접 재현하고 해결하는 과정에 집중한다.

개발 기간과 일정은 `docs/.internal/PROGRESS.md`에서 관리한다. (커밋 제외)


## 2. 프로젝트 목표

1. 상품 및 재고 조회 기능을 구현한다.
2. 주문 생성 과정에서 재고 정합성을 보장한다.
3. 동시 주문 상황에서 초과 판매가 발생하지 않도록 한다.
4. 대량 주문 데이터 조회 성능을 측정하고 개선한다.
5. Redis를 활용하여 상품 조회 캐시를 구현한다.
6. 주문 데이터를 기반으로 통계 Batch를 구현한다.
7. 실제 AWS 환경에 서비스를 배포한다.
8. 배포 환경에서 최종 성능을 검증한다.
9. Claude Code를 개발 과정의 분석, 구현, 테스트, 문제 해결에 활용한다.


## 3. 프로젝트 범위

### 포함

- 상품 조회
- 재고 조회
- 주문 생성
- 주문 조회
- 주문 상태 관리
- 주문 통계
- 테스트 데이터 생성
- 동시성 테스트
- DB 성능 테스트
- DB 성능 개선
- Redis 상품 캐시
- 주문 통계 Batch
- 단위 테스트
- 통합 테스트
- Docker
- AWS 배포
- 기본 GitHub Actions

### 제외

다음 기능은 구현하지 않는다.

- 회원가입
- 로그인
- 인증/인가
- 결제
- 장바구니
- 쿠폰
- 포인트
- 배송
- Frontend
- Kafka
- Kubernetes
- Elasticsearch
- Microservices


## 4. 기술 스택

### Backend

- Java 21
- Spring Boot 3.5.16
- Spring Data JPA
- QueryDSL (미확정)
- Gradle (Groovy DSL)

### Database

- PostgreSQL 17
- Redis 7.4
- Flyway

### Test

- JUnit 5
- Testcontainers PostgreSQL

### Performance Test

- k6

### Infrastructure

- Docker
- AWS EC2
- AWS RDS PostgreSQL
- AWS ElastiCache Redis

### CI

- GitHub Actions


## 5. 프로젝트 구조

```text
orderflow/
├── CLAUDE.md
├── README.md
├── .gitignore
├── docker-compose.yml
├── docs/
│   ├── PROJECT.md
│   ├── BACKEND.md
│   ├── DEVELOPMENT_RULES.md
│   ├── decisions/
│   └── .internal/           # 커밋 제외
│       ├── HANDOVER.md      # 직전 작업 인수인계 (세션 종료 시 갱신)
│       ├── PROGRESS.md      # 개발 기간 / 일정 / 진행 상황
│       └── CHECKLIST.md     # 일별 작업 체크리스트
├── backend/
├── performance/
├── infra/
└── scripts/
```

`docs/.internal/` 경로는 `.gitignore`에 등록하며 저장소에 커밋하지 않는다.

개발 기간, 일정, 진행 상황, 체크리스트는 `docs/.internal/` 내 파일에만 기록하고
커밋 대상 문서에는 작성하지 않는다.

## 6. 핵심 검증 시나리오
시나리오 1. 주문 생성
주문 요청
    ↓
사용자 확인
    ↓
상품 확인
    ↓
재고 조회 및 잠금
    ↓
재고 차감
    ↓
주문 생성
    ↓
주문 완료

주문 생성과 재고 차감은 하나의 트랜잭션으로 처리한다.

재고가 부족하거나 처리 중 오류가 발생하면 관련 변경 사항을 함께 롤백한다.

시나리오 2. 재고 동시성

재고가 100개인 상품에 동시에 여러 주문 요청을 발생시킨다.

예:

재고 = 100

동시 요청 = 100건
요청당 수량 = 2

다음 조건을 만족해야 한다.

재고가 음수가 되지 않는다.
재고보다 많은 수량의 주문이 성공하지 않는다.
주문 성공 수량과 실제 재고 감소량이 일치한다.
동시성 제어

재고 차감에는 Pessimistic Lock을 사용한다.

선택 이유:

재고 정합성이 핵심인 기능이다.
구현이 명확하다.
충돌 발생 시 DB Lock을 통해 순차적으로 처리할 수 있다.
불필요한 구현 범위를 줄일 수 있다.

Optimistic Lock과의 비교 구현은 프로젝트 범위에서 제외한다.

시나리오 3. 대량 주문 조회

주문 데이터를 대량으로 생성한 후 주문 조회 API의 성능을 측정한다.

주요 API:

GET /api/orders?userId={userId}

측정 항목:

평균 응답시간
p95
p99
RPS
Error Rate

필요한 경우 PostgreSQL Execution Plan을 함께 분석한다.

시나리오 4. 주문 통계

주문 데이터를 기반으로 일별 통계를 생성한다.

집계 항목:

총 주문 수
총 판매 금액

동일한 날짜의 Batch가 여러 번 실행되어도 통계 데이터가 중복 생성되지 않아야 한다.

## 7. 테스트 데이터 전략

데이터 규모를 세 단계로 구분한다.

단계	주문 데이터	용도
SMALL	10,000건	개발 및 기능 테스트
MEDIUM	100,000건	성능 Baseline 및 개선
LARGE	1,000,000건	최종 배포 환경 검증

### 생성 원칙

테스트 데이터는 재현 가능해야 한다.

고정 Seed를 사용한다.

개발/테스트 환경의 기본 Seed는 다음과 같이 사용한다.

- Seed: `12345`
- User: 100명
- Product: 100개

Product 가격 범위와 Stock 초기 수량 범위는 테스트 데이터 생성기 구현 전에 결정한다.

개발 과정에서는 SMALL 데이터를 기본으로 사용한다.
성능 개선 과정에서는 MEDIUM 데이터를 사용한다.
LARGE 데이터는 최종 검증 시 1회 생성한다.

PostgreSQL Bulk Insert를 사용하여 대량 데이터를 생성한다.

## 8. 성능 검증 전략

성능 목표를 개발 시작 전에 임의로 정하지 않는다.

다음 순서로 진행한다.

100K 데이터 생성
      ↓
Baseline 측정
      ↓
Execution Plan 분석
      ↓
병목 원인 파악
      ↓
개선 적용
      ↓
재측정
      ↓
Before / After 비교

성능 개선은 최대 1~2개의 핵심 개선에 집중한다.

예:

적절한 Index 추가
Query 개선

개선 효과가 확인되면 추가적인 최적화는 프로젝트 범위에서 제외한다.

초기 목표

Baseline 측정 후 다음 기준을 참고하여 목표를 설정한다.

평균 응답시간 < 500ms
p99 < 2초
RPS > 100

단, 실제 결과와 실행 환경을 고려하여 최종 목표를 결정한다.

목표를 달성하지 못한 경우에도 실제 측정 결과를 그대로 기록한다.

## 9. Redis

Redis는 상품 상세 조회 캐시에만 사용한다.

대상 API:

GET /api/products/{productId}

Cache Key

product:{productId}

TTL

10분

처리 흐름

API 요청
↓
Redis 조회
┌──────┴──────┐
Hit           Miss
↓              ↓
응답         PostgreSQL
↓
Redis 저장

상품 데이터 변경 기능은 프로젝트 범위에 포함하지 않는다.

향후 상품 변경 기능이 추가될 경우 DB 변경 성공 후 해당 상품의 Cache를 삭제한다.

### 검증

상품 조회 API에 대해 다음을 확인한다.

Cache Miss 발생
DB 조회
Cache 저장
Cache Hit 발생
Cache Hit 이후 DB 조회 감소

Cache Hit/Miss는 로그를 통해 확인한다.

별도의 Cache Hit/Miss 메트릭 수집 시스템은 현재 프로젝트 범위에 포함하지 않는다.

## 10. Batch

Spring Batch를 사용하여 주문 통계를 생성한다.

### 실행 방식

초기에는 HTTP Endpoint를 통해 수동 실행한다.

예:

POST /internal/batch/order-statistics?date=2026-08-23

해당 Endpoint는 내부 테스트용이며 인증 기능은 구현하지 않는다.

### 멱등성

Batch의 기준 날짜를 JobParameter로 사용한다.

date = 2026-08-23

멱등성은 Skip 방식으로 처리한다.

동일 날짜에 SUCCESS JobExecution이 존재하면 통계를 다시 생성하지 않고 Skip한다.

통계 테이블에는 다음 Unique 제약을 적용한다.

stat_date UNIQUE

UNIQUE 제약은 중복 생성을 막는 최종 안전장치이며, UPSERT는 사용하지 않는다.

동일 날짜의 Batch를 여러 번 실행해도 통계 데이터가 중복 생성되지 않아야 한다.

### Job Status

Spring Batch의 JobExecution 상태를 기준으로 처리한다.

SUCCESS
→ 기존 통계를 유지하고 Skip한다.

FAILED
→ 재실행할 수 있어야 한다.

### 검증

같은 날짜로 Batch를 여러 번 실행한다.

SUCCESS JobExecution 존재
↓
Skip

FAILED JobExecution
↓
재실행

동일 날짜의 통계 데이터는 중복 생성되지 않아야 한다.

## 11. 배포
Application
AWS EC2
Docker Container
Database
AWS RDS PostgreSQL
Redis
AWS ElastiCache Redis
배포 방식

초기에는 수동 배포한다.

Docker Image를 빌드한 후 EC2에서 실행한다.

GitHub Actions는 다음 범위로 최소 구성한다.

Push
↓
Build
↓
Unit Test
↓
Docker Build

GitHub Actions의 구체적인 jobs, steps, artifacts 및 Docker Image Registry 사용 여부는 백엔드 프로젝트 완료 후 결정한다.

AWS 자동 배포는 프로젝트의 필수 범위에서 제외한다.
초기 AWS 배포는 수동으로 진행한다.

## 12. 모니터링 및 로그
Application

Spring Boot 로그를 stdout으로 출력한다.

다음 정보를 로그로 확인할 수 있어야 한다.

요청 처리 결과
예외
Batch 실행 결과

Redis는 필요한 경우 Cache Hit/Miss를 로그로 확인한다.

AWS

CloudWatch의 기본 메트릭을 활용한다.

EC2 CPU
Network
RDS CPU
RDS Connection

성능 테스트는 k6를 로컬에서 실행하고 결과를 프로젝트 문서에 기록한다.

## 13. 완료 기준
 상품 조회 API
 재고 조회 API
 주문 생성 API
 주문 조회 API
 주문/재고 Transaction
 Pessimistic Lock
 동시성 테스트
 10K 테스트 데이터 생성
 100K 성능 테스트
 DB 성능 개선
 Before / After 비교
 Redis 상품 캐시
 Cache Hit/Miss 검증
 주문 통계 Batch
 Batch 멱등성
 단위 테스트
 통합 테스트
 Docker
 AWS 배포
 1M 최종 검증
 GitHub Actions
 기술적 의사결정 문서화
 Claude Code 활용 과정 문서화

## 14. 프로젝트의 최종 목적

OrderFlow의 목적은 많은 기능을 구현하는 것이 아니다.

다음 질문에 답할 수 있는 프로젝트를 만드는 것이다.

어떤 문제가 발생했는가?

왜 문제가 발생했는가?

어떤 방법으로 해결했는가?

왜 해당 방법을 선택했는가?

실제 테스트에서 어떤 결과가 나왔는가?

AI를 개발 과정에서 어떻게 활용했는가?
