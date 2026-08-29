package com.orderflow.common.exception;

import java.time.LocalDateTime;

/**
 * API 규칙의 에러 응답.
 */
public record ErrorResponse(String status, Error error) {

    private static final String ERROR = "error";

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                ERROR,
                new Error(errorCode.name(), errorCode.getMessage(), LocalDateTime.now()));
    }

    public record Error(String code, String message, LocalDateTime timestamp) {
    }
}
