package com.teumteum.global.common;

import jakarta.validation.constraints.NotBlank;

/**
 * 입력값 검증 실패 항목 1개.
 * <pre>
 * { "field": "email", "message": "이메일 형식이 아니에요." }
 * </pre>
 * 사용자가 입력한 값(rejected value)은 넣지 않는다. 비밀번호 등이 노출될 수 있음 (NFR-005)
 */
public record FieldErrorDetail(
        String field,
        String message
) {
}
