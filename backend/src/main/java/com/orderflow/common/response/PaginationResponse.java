package com.orderflow.common.response;

import org.springframework.data.domain.Page;

/**
 * BACKEND.md 3. API 규칙의 목록 응답 pagination 필드.
 * page는 0부터 시작한다.
 */
public record PaginationResponse(int page, int size, long total, int totalPages) {

    public static PaginationResponse from(Page<?> page) {
        return new PaginationResponse(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
