# Sequence allocationSize 결정

## 문제

PK를 `GenerationType.SEQUENCE`로 발급하면 INSERT할 때마다 DB에서 다음 ID를 받아와야 한다.
매 건마다 `nextval`을 호출하면 저장 건수만큼 DB 왕복이 발생한다.

Hibernate는 `nextval`을 한 번 호출해 ID 구간을 통째로 받아두고 그 구간이 소진될 때까지
메모리에서 ID를 발급한다(pooled 할당). 한 번에 받아올 구간의 크기(`allocationSize`)를 정해야 한다.

이 프로젝트는 Order 데이터를 10K / 100K / 1M 규모로 생성하므로 대량 INSERT 경로가 존재한다.

## 선택지

| | 값 | 내용 |
|---|---|---|
| A | 1 | 저장 1건마다 `nextval` 호출 |
| B | 50 | JPA 표준 `@SequenceGenerator`의 기본값 |
| C | 50보다 큰 값 | 왕복을 더 줄이는 대신 구간을 더 크게 선점 |

어느 값을 고르든 DB 시퀀스의 `INCREMENT BY`를 같은 값으로 맞춰야 한다.

## 비교 기준

- DB 왕복 횟수 (측정)
- ID 연속성 및 미사용 ID 낭비
- 표준 기본값 여부
- 두 설정이 어긋났을 때의 안전성

## 선택

**B — `allocationSize = 50`, DB 시퀀스도 `INCREMENT BY 50`.**

```sql
CREATE SEQUENCE users_seq INCREMENT BY 50;
```

```java
@SequenceGenerator(name = "users_seq", sequenceName = "users_seq", allocationSize = 50)
```

## 선택 이유

### DB 왕복이 실제로 줄어든다

`UserSeedTest`(User 61건 저장)를 `allocationSize`만 바꿔 실행하고
Hibernate SQL 로그의 `nextval` 호출 횟수를 셌다.

| allocationSize | INSERT | `nextval` 호출 |
|---:|---:|---:|
| 1 | 61회 | 61회 |
| 50 | 61회 | 2회 |

측정은 DB `INCREMENT BY`와 `allocationSize`를 같은 값으로 맞춘 정상 상태에서 했다.
50에서 2회인 이유는 첫 구간을 소진한 뒤 한 번 더 받아왔기 때문이다.

100만 건을 생성하면 A는 `nextval` 100만 회, B는 약 2만 회가 된다.

측정한 것은 **왕복 횟수뿐**이다. 응답 시간과 처리량은 측정하지 않았으므로
속도가 얼마나 빨라지는지는 근거가 없다. (BACKEND.md 16)

### A를 선택하지 않은 이유

왕복 횟수가 저장 건수와 같아진다. 대량 INSERT 경로에서 그대로 비용이 된다.

### C를 선택하지 않은 이유

왕복은 더 줄지만 구간을 크게 선점하므로, 애플리케이션이 구간을 다 쓰기 전에 종료되면
그만큼 ID가 크게 건너뛴다. 왕복 감소 폭도 50 이후로는 체감이 작다(1/50 → 1/500).

현재 ID 연속성이 필요한 요구사항은 없지만, 표준 기본값을 벗어날 근거도 없다.

### 50이 JPA 표준 기본값이다

`jakarta.persistence.SequenceGenerator`의 `allocationSize` 기본값이 50이다.
기본값을 쓰면 Entity 쪽 설정이 특별할 이유가 없고 DB 시퀀스만 맞추면 된다.

## 검증 결과

### 왕복 횟수

위 표가 측정 결과다. 재현 방법은 문서 끝에 적는다.

### 두 값이 어긋나면 애플리케이션이 기동되지 않는다

양방향 모두 재현했다. (Hibernate 6.6.53 / `ddl-auto: validate`)

DB `INCREMENT BY 1` + `allocationSize = 50`

```text
org.hibernate.MappingException: The increment size of the [users_seq] sequence
is set to [50] in the entity mapping while the associated database sequence
increment size is [1]
```

DB `INCREMENT BY 50` + `allocationSize = 1`

```text
org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation:
sequence [users_seq] defined inconsistent increment-size;
found [50] but expecting [1]
```

불일치는 런타임 PK 충돌로 나타나기 전에 기동 단계에서 차단된다.
데이터가 깨질 위험은 없으나, 스키마와 Entity 중 한쪽만 고치면 애플리케이션이 뜨지 않는다.
두 값은 항상 함께 변경한다.

### PK는 연속하지 않는다

pooled 할당은 구간을 미리 받아두므로 애플리케이션이 구간을 다 쓰기 전에 종료되면
남은 ID는 버려진다. ID를 순번이나 건수로 해석하면 안 된다. 건수는 `COUNT`로 센다.

### 테스트용 Seed가 ID를 직접 지정하는 경우

시퀀스 값은 트랜잭션 롤백 대상이 아니다. 테스트가 롤백되어도 시퀀스는 되돌아가지 않는다.

Seed 뒤로 시퀀스를 밀 때는 현재 값보다 **앞으로만** 이동시켜야 한다.
되감으면 Hibernate가 메모리에 들고 있는 구간과 겹쳐 같은 세션에서 동일한 ID가 두 번 발급된다.
(`NonUniqueObjectException`)

```sql
SELECT setval('users_seq',
    GREATEST((SELECT MAX(id) FROM users), (SELECT last_value FROM users_seq)));
```

### 재현 방법

`UserSeedTest`가 이 결정을 검증한다.

저장 1건짜리 테스트는 Hibernate가 이미 확보한 구간 안에서 끝나 재할당 경로를 지나지 않는다.
`allocationSize`를 넘기는 건수(60건)를 저장해야 두 번째 `nextval`이 실행된다.

시퀀스 상태는 DB에 남으므로 확인 전에 초기 상태를 만들어야 결과를 신뢰할 수 있다.

```bash
docker exec orderflow-postgres psql -U orderflow -d orderflow \
  -c "ALTER SEQUENCE users_seq RESTART;"

cd backend && ./gradlew test --tests '*UserSeedTest*' --rerun-tasks
```

`nextval` 호출 횟수는 테스트 결과 XML에서 센다.

```bash
grep -o nextval build/test-results/test/TEST-com.orderflow.user.UserSeedTest.xml | wc -l
```
