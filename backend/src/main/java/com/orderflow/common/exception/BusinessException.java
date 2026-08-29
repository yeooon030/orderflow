package com.orderflow.common.exception;

/**
 * Spring의 기본 롤백 규칙은 unchecked exception에만 적용되므로 RuntimeException을 상속한다.
 * checked exception으로 두면 주문 생성 Transaction이 전체 롤백되지 않는다. (BACKEND.md 7)
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
