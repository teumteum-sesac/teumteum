package com.teumteum.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teumteum.global.exception.ErrorCode;

import java.util.List;

/**
 * 실패 응답의 "error" 부분.
 * <pre>
 * { "code": "INVALID_REQUEST", "message": "입력값을 확인해 주세요.", "fieldErrors": [ ... ] }
 * </pre>
 * fieldErrors 는 검증 실패일 때만 들어가고, 값이 없으면 JSON 에서 빠진다.
 */
public record ErrorDetail(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<FieldErrorDetail> fieldErrors
) {

    public static ErrorDetail of(ErrorCode errorCode) {
        return new ErrorDetail(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ErrorDetail of(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        return new ErrorDetail(errorCode.getCode(), errorCode.getMessage(), fieldErrors);
    }
}
