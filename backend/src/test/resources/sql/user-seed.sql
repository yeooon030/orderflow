-- 테스트 전용 User Seed.
--
-- Flyway Migration이 아니라 @Sql로만 적재하므로 배포 환경 스키마에는 반영되지 않는다.
-- 대량 데이터 생성기는 Day 6 TestDataGenerator에서 별도로 다룬다.
--
-- 인원 수는 BACKEND.md 9. Test Data의 기본 Seed(User 100명)를 따른다.
-- created_at은 테스트가 실행 시각에 의존하지 않도록 고정값을 사용한다.

INSERT INTO users (id, name, created_at)
SELECT i,
       '사용자' || LPAD(i::text, 3, '0'),
       TIMESTAMP '2026-01-01 00:00:00'
FROM generate_series(1, 100) AS i;

-- Seed가 id를 직접 지정하므로 users_seq를 그 뒤로 밀어 둔다.
-- 이 과정이 없으면 JPA가 발급하는 id가 Seed와 겹쳐 PK 충돌이 발생한다. (BACKEND.md 18)
--
-- GREATEST로 현재 값과 비교해 앞으로만 이동시킨다. 시퀀스를 되감으면 안 되기 때문이다.
-- Hibernate의 pooled 할당기는 발급 범위를 EntityManagerFactory 수명 동안 메모리에 들고 있는데,
-- 이 스크립트는 테스트마다 실행된다. 되감으면 이미 나눠준 범위를 다시 발급해
-- 같은 세션에서 동일한 id가 두 번 나온다.
SELECT setval(
        'users_seq',
        GREATEST((SELECT MAX(id) FROM users), (SELECT last_value FROM users_seq)));
