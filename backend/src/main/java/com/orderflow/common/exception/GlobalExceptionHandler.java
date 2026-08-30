package com.orderflow.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * ResponseEntityExceptionHandler를 상속해 Spring MVC의 표준 예외를 넘겨받는다.
 *
 * 상속하지 않고 @ExceptionHandler(Exception.class)만 두면 파라미터 누락, 본문 파싱 실패,
 * 타입 불일치 같은 클라이언트 입력 오류까지 전부 가로채 500으로 응답한다.
 * Spring이 이들을 400/404/405/415로 변환할 기회를 얻기 전에 매칭되기 때문이다.
 *
 * 상속하면 표준 예외는 아래 handleExceptionInternal로 들어오고,
 * @ExceptionHandler(Exception.class)에는 진짜 예상하지 못한 예외만 남는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.from(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * Spring MVC 표준 예외의 응답 본문을 공통 에러 응답 형식으로 바꾼다. (BACKEND.md 3)
     *
     * 클라이언트 입력 오류이므로 ERROR 로그를 남기지 않는다.
     * 남기면 정상 운영 중에도 서버 장애처럼 보여 진짜 예외를 찾기 어려워진다.
     *
     * headers를 그대로 넘긴다. 405 응답의 Allow 헤더처럼 Spring이 채워 준 정보가 있다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        return new ResponseEntity<>(ErrorResponse.from(errorCodeOf(statusCode)), headers, statusCode);
    }

    private ErrorCode errorCodeOf(HttpStatusCode statusCode) {
        if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErrorCode.ENDPOINT_NOT_FOUND;
        }
        if (statusCode.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (statusCode.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        if (statusCode.is4xxClientError()) {
            return ErrorCode.INVALID_REQUEST;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
