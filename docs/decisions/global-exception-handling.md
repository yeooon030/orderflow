# 전역 예외 처리 범위 결정

## 문제

전역 예외 처리를 `@RestControllerAdvice` 하나로 두고, 처리하지 못한 예외를 위해
catch-all을 두었다.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("처리되지 않은 예외", e);
    return ... 500 INTERNAL_ERROR ...;
}
```

`Exception.class`는 모든 예외에 매칭된다. Spring MVC가 자체 예외를 400/404/405/415로
변환하기 전에 이 핸들러가 먼저 조회되어 전부 500이 된다.

`@ExceptionHandler`는 `DefaultHandlerExceptionResolver`보다 먼저 실행되기 때문이다.

클라이언트 입력 오류가 서버 오류로 응답된다.

## 선택지

| | 방식 | 내용 |
|---|---|---|
| A | 현행 유지 | catch-all만 둔다 |
| B | 개별 핸들러 추가 | 필요한 예외마다 `@ExceptionHandler`를 단다 |
| C | `ResponseEntityExceptionHandler` 상속 | Spring이 정의한 표준 예외 처리를 물려받고 응답 형식만 바꾼다 |

## 비교 기준

- 잘못된 Status로 응답되는 경우가 남는가 (측정)
- 새로운 예외가 추가될 때의 누락 위험
- 응답 형식 유지

## 선택

**C — `ResponseEntityExceptionHandler`를 상속하고 `handleExceptionInternal`만 재정의한다.**

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        return new ResponseEntity<>(ErrorResponse.from(errorCodeOf(statusCode)), headers, statusCode);
    }
}
```

## 선택 이유

### A는 정상적으로 발생하는 요청까지 500으로 만든다

측정 결과는 아래에 있다. `GET /api/orders`를 필수 파라미터 없이 호출하는 경우도 500이었다.

500은 서버 문제를 뜻한다. 클라이언트는 자기 요청이 잘못됐다는 사실을 알 수 없고,
재시도해도 같은 결과를 받는다.

### B를 선택하지 않은 이유

지금 필요한 예외에만 핸들러를 달면 나머지는 500으로 남는다.
새 API가 다른 종류의 입력 오류를 만들 때마다 핸들러를 추가해야 하고,
빠뜨리면 다시 500이 된다. **빠뜨렸다는 사실이 드러나지 않는다.**

C는 Spring이 이미 정의해 둔 표준 예외 처리를 물려받으므로 목록을 따로 관리하지 않는다.

### 응답 형식은 그대로 유지된다

`handleExceptionInternal`만 재정의해 본문을 공통 `ErrorResponse`로 바꾼다.
`status` / `error.code` / `error.message` / `error.timestamp` 구조는 변하지 않는다. (BACKEND.md 3)

`headers`는 그대로 넘긴다. 405 응답의 `Allow` 헤더처럼 Spring이 채워 준 정보가 있다.

Java 예외 메시지와 클래스명은 본문에 넣지 않는다. `ErrorCode`에 정의한 고정 문자열만 나간다.

## 검증 결과

### 수정 전 — 12가지가 모두 500

실제 응답을 확인한 결과다.

```text
500  본문이 깨진 JSON
500  본문 없음
500  본문의 타입 불일치        {"userId":"abc"}
500  items가 배열이 아님
500  Content-Type이 text/plain
500  필수 파라미터 누락         GET /api/orders
500  status 값이 잘못됨         ?status=WRONG
500  날짜 형식이 잘못됨         ?startDate=2026/08/30
500  userId가 숫자가 아님
500  orderId가 숫자가 아님      GET /api/orders/abc
500  지원하지 않는 메서드       DELETE /api/orders/1
500  없는 경로
```

본문은 전부 아래와 같았다.

```json
{"status":"error","error":{"code":"INTERNAL_ERROR",
 "message":"서버 오류가 발생했습니다.","timestamp":"..."}}
```

### 수정 후

```text
400 INVALID_REQUEST          본문·파라미터 관련 9가지
405 METHOD_NOT_ALLOWED       DELETE /api/orders/1
415 UNSUPPORTED_MEDIA_TYPE   Content-Type 불일치
404 ENDPOINT_NOT_FOUND       없는 경로
```

### 회귀를 막는 테스트가 있다

`RequestErrorHandlingTest` 8건이 위 응답을 검증한다.

`extends ResponseEntityExceptionHandler`를 제거하고 실행하면 **8건 전부 실패한다.**
이 테스트는 결함을 실제로 잡는다.

## 적용 방법

### catch-all은 마지막 수단으로만 남긴다

`@ExceptionHandler(Exception.class)`는 유지하되, 표준 예외가 그리로 흘러가지 않는 상태를
전제로 한다. 이 핸들러에 로그가 찍힌다면 정말로 예상하지 못한 예외다.

### 클라이언트 입력 오류에는 ERROR 로그를 남기지 않는다

수정 전에는 `GET /api/orders`를 파라미터 없이 호출한 것만으로 스택트레이스가 남았다.
정상 운영 중에도 ERROR 로그가 쌓이면 진짜 장애를 로그에서 찾기 어려워진다.

### Error Code는 도메인과 요청 형식을 구분한다

값이 오지 않은 것과 존재하지 않는 값을 보낸 것은 다르다.

```text
userId가 요청에 없음        → INVALID_REQUEST (400)
존재하지 않는 userId를 보냄  → USER_NOT_FOUND  (404)
```

`*_NOT_FOUND`를 "값이 없다"는 뜻으로 쓰면 클라이언트가 자기 요청이 잘못됐는지
서버에 데이터가 없는지 구분할 수 없다.
