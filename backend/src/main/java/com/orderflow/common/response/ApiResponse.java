package com.orderflow.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * API 규칙의 성공 응답.
 * 단건: {status, data}
 * 목록: {status, data, pagination}
 * (pagination이 없는 응답에서는 필드 자체를 내보내지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String status, T data, PaginationResponse pagination) {

    private static final String SUCCESS = "success";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS, data, null);
    }

    public static <T> ApiResponse<List<T>> success(List<T> data, PaginationResponse pagination) {
        return new ApiResponse<>(SUCCESS, data, pagination);
    }
}
