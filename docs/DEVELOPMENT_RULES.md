# OrderFlow Development Rules

## 1. 기본 원칙

OrderFlow는 개인 프로젝트다.

기능을 추가하는 것보다 핵심 문제를 직접 경험하고 해결하는 것을 우선한다.

불필요한 기술과 추상화를 추가하지 않는다.

개발 기간과 일정은 `docs/.internal/PROGRESS.md`를 따른다. (커밋 제외)


## 2. 구현 우선순위

항상 다음 순서를 따른다.

```text
요구사항 확인
 ↓
기존 코드 확인
 ↓
구현 계획
 ↓
구현
 ↓
테스트
 ↓
검증
 ↓
커밋
```

## 3. 기존 코드 확인

작업 전에 관련 코드를 확인한다.

특히 다음을 확인한다.

프로젝트 구조
Entity
Repository
Service
Controller
테스트
DB 구조

기존 구현을 확인하지 않고 새로운 구조를 임의로 만들지 않는다.

## 4. 기술 스택

다음 기술 스택을 기준으로 개발한다.

```text
Backend
- Java 21
- Spring Boot 3.5.16
- Spring Data JPA
- QueryDSL (미확정)
- Gradle (Groovy DSL)

Database
- PostgreSQL 17
- Redis 7.4
- Flyway

Test
- JUnit 5
- Testcontainers PostgreSQL

Performance
- k6

Infrastructure
- Docker
- AWS EC2
- AWS RDS PostgreSQL
- AWS ElastiCache Redis

CI
- GitHub Actions
```
새로운 기술 추가

새로운 라이브러리나 기술을 추가하기 전에 다음을 확인한다.

기존 기술로 해결할 수 없는가?
프로젝트 목표에 필요한가?
유지보수 비용이 증가하지 않는가?

단순히 기술 스택을 늘리기 위해 라이브러리를 추가하지 않는다.

새로운 기술이 필수인 경우 도입 이유와 대안을 먼저 확인한다.

## 5. 설계 원칙

Controller는 HTTP 요청/응답 처리에 집중한다.

Service는 비즈니스 로직을 담당한다.

Repository는 데이터 접근을 담당한다.

Transaction 경계를 명확하게 관리한다.

## 6. Database

DB 변경 시 다음을 확인한다.

PK
FK
UNIQUE
NOT NULL
Index
Transaction

Index는 실제 쿼리와 Execution Plan을 확인한 후 추가한다.

추측만으로 Index를 추가하지 않는다.

## 7. JPA

다음 문제를 항상 확인한다.

N+1
Lazy Loading
불필요한 Entity 조회
Fetch Join
Transaction 경계

성능 문제가 발생하면 실제 SQL을 확인한다.

## 8. 동시성

재고 차감은 Pessimistic Lock을 사용한다.

Optimistic Lock과 비교 구현하지 않는다.

목표는 두 방식을 비교하는 것이 아니라 실제 동시 주문 상황에서 재고 정합성을 보장하는 것이다.

동시성 문제는 실제 여러 DB Connection을 사용하는 테스트로 검증한다.

## 9. 테스트

기능 구현 후 관련 테스트를 작성한다.

필수 테스트:

주문 생성
재고 차감
재고 부족
Transaction Rollback
동시 주문
주문 조회

테스트를 통과하지 않은 기능은 완료된 것으로 판단하지 않는다.

## 10. Test Data

개발 단계별 데이터 규모를 명확하게 구분한다.

SMALL
10K Orders
→ 기능 개발 / 테스트

MEDIUM
100K Orders
→ 성능 테스트 / 개선

LARGE
1M Orders
→ 최종 배포 환경 검증

테스트 데이터는 고정 Seed를 사용한다.

개발/테스트 환경의 기본 Seed는 다음과 같다.

- Seed = `12345`
- User = 100명
- Product = 100개

Product 가격 범위와 Stock 초기 수량 범위는 테스트 데이터 생성기 구현 전에 결정한다.

1M 데이터는 개발 과정에서 반복 생성하지 않는다.

최종 검증 단계에서 1회 생성한다.

## 11. Performance

성능 개선은 반드시 측정 기반으로 한다.

Baseline
 ↓
원인 분석
 ↓
개선
 ↓
재측정
 ↓
비교

측정하지 않은 성능 수치를 작성하지 않는다.

성능 개선은 최대 1~2개의 핵심 문제에 집중한다.

핵심 개선 효과가 확인되면 추가적인 미세 최적화는 진행하지 않는다.

## 12. Redis

Redis는 상품 조회 캐시에만 사용한다.

product:{productId}

TTL = 10 minutes

재고 데이터에는 Redis Cache를 적용하지 않는다.

Cache 적용 후 반드시 다음을 확인한다.

Miss
→ DB 조회
→ Cache 저장

Hit
→ Redis 응답

Cache Hit/Miss는 로그를 통해 검증한다.

별도의 Cache Hit/Miss 메트릭 수집 시스템은 사용하지 않는다.

## 13. Batch

Spring Batch를 사용한다.

통계 기준 날짜를 JobParameter로 사용한다.

date=YYYY-MM-DD

멱등성은 Skip 방식으로 처리한다.

동일 날짜에 SUCCESS JobExecution이 존재하면 통계를 다시 생성하지 않고 Skip한다.

통계 테이블의 날짜 컬럼에 UNIQUE 제약을 사용하여 중복 생성을 방지한다.

UNIQUE 제약은 중복 생성을 막는 최종 안전장치이며, UPSERT는 사용하지 않는다.

### Job Status

동일 날짜 Batch의 JobExecution 상태를 기준으로 처리한다.

SUCCESS
→ 기존 통계를 유지하고 Skip한다.

FAILED
→ 재실행 가능하도록 한다.

동일 날짜 Batch를 여러 번 실행하는 테스트를 작성한다.

## 14. API

API 응답 형식을 임의로 변경하지 않는다.

성공:

{
  "status": "success",
  "data": {}
}

에러:

{
  "status": "error",
  "error": {
    "code": "ERROR_CODE",
    "message": "error message",
    "timestamp": "2026-08-23T10:30:00"
  }
}

금액은 Long을 사용한다.

날짜/시간은 ISO-8601 형식을 사용한다.

## 15. 코드 품질

다음 원칙을 따른다.

명확한 이름
작은 책임
중복 최소화
불필요한 추상화 금지
불필요한 주석 금지
비즈니스 의도가 필요한 곳에는 주석 작성

짧은 코드보다 이해하기 쉬운 코드를 우선한다.

## 16. 새로운 라이브러리

새로운 라이브러리를 추가하기 전에 다음을 확인한다.

기존 기술로 해결할 수 없는가?
프로젝트 목표에 필요한가?
유지보수 비용이 증가하지 않는가?

단순히 기술 스택을 늘리기 위해 라이브러리를 추가하지 않는다.

## 17. Git
커밋 메시지는 다음 형식을 따른다.

<type>: <description>

### Type
- feat: 새로운 기능
- fix: 버그 수정
- refactor: 기능 변경 없는 코드 개선
- test: 테스트 추가/수정
- docs: 문서 수정
- chore: 설정 및 기타 작업
- perf: 성능 개선

의미 있는 작업 단위로 커밋한다.

예:
feat: 상품 조회 API 구현
feat: 주문 생성 API 구현
test: 주문 생성 통합 테스트 추가
test: 재고 동시성 테스트 추가
perf: 주문 조회 인덱스 최적화
feat: 상품 조회 Redis 캐시 추가
feat: 주문 통계 Batch 구현
fix: 재고 동시성 문제 수정

### 커밋 제외 경로

`docs/.internal/` 경로는 저장소에 커밋하지 않는다.

해당 경로에는 개발 기간, 일정, 진행 상황, 체크리스트 등 공개 문서에 포함하지 않는 내용을 둔다.

`.gitignore`에 다음을 등록한다.

```text
docs/.internal/
```

`docs/.internal/`의 내용을 커밋 대상 문서로 옮겨 적지 않는다.

### Git Branch Strategy

main
└── feature/*

### GitHub Actions

GitHub Actions의 기본 CI 범위는 다음과 같다.

Push
↓
Build
↓
Unit Test
↓
Docker Build

구체적인 jobs, steps, artifacts 및 Docker Image Registry 사용 여부는 백엔드 프로젝트 완료 후 결정한다.

AWS 자동 배포는 구현하지 않는다.

## 18. Claude Code 활용

Claude Code를 단순 코드 자동 생성 도구로 사용하지 않는다.

작업 시작 전 `docs/.internal/PROGRESS.md`를 먼저 확인하고 현재 Phase를 파악한다.

다음 과정에서 적극적으로 활용한다.

분석
프로젝트 구조 분석
코드 분석
로그 분석
SQL 분석
오류 원인 분석
설계
API 설계 검토
DB 설계 검토
동시성 문제 분석
성능 개선안 검토
구현
코드 작성
테스트 작성
리팩토링
문서 작성
검증

Claude Code가 작성한 결과를 그대로 신뢰하지 않는다.

반드시 직접 검증한다.

요구사항 충족 여부
테스트 결과
Transaction 동작
동시성
SQL
성능

## 19. AI 활용 기록

의미 있는 AI 활용 사례는 별도로 기록한다.

예:

문제
 ↓
Claude Code에 분석 요청
 ↓
AI가 제시한 원인
 ↓
검증 과정
 ↓
최종 원인
 ↓
해결 방법
 ↓
결과

AI의 답변을 그대로 기술적 근거로 사용하지 않는다.

실제 실행 결과와 측정 결과를 최종 근거로 사용한다.

## 20. 기술적 의사결정

다음과 같은 중요한 결정은 docs/decisions/에 기록한다.

Pessimistic Lock 선택
Index 선택
Query 개선
Redis 적용
Batch 멱등성 설계

문서 형식:

문제
↓
선택지
↓
비교 기준
↓
선택
↓
선택 이유
↓
검증 결과

## 21. 완료 검증

작업 완료 전에 확인한다.

컴파일 성공
관련 테스트 통과
기존 테스트 통과
요구사항 충족
예외 처리
필요한 경우 성능 측정
변경 사항 확인

검증하지 않은 내용을 완료했다고 보고하지 않는다.

## 22. 범위 통제 원칙

다음 상황에서는 범위를 확장하지 않는다.

새로운 기술이 재미있어 보이는 경우
더 복잡한 아키텍처가 좋아 보이는 경우
추가 기능이 있으면 좋을 것 같은 경우
성능을 더 개선할 수 있는 경우

우선순위는 다음과 같다.

핵심 기능 완성
 >
데이터 정합성
 >
동시성 검증
 >
성능 개선
 >
배포
 >
문서화
 >
추가 기능

## 23. 최종 원칙

OrderFlow의 목표는 "AI로 코드를 많이 작성했다"가 아니다.

다음 질문에 답할 수 있어야 한다.

어떤 문제를 해결했는가?

왜 그 문제가 발생했는가?

어떤 기술적 선택을 했는가?

그 선택이 실제로 효과가 있었는가?

그 과정에서 AI를 어떻게 활용했고, AI의 결과를 어떻게 검증했는가?
