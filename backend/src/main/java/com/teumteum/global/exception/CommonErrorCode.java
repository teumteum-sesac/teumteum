package com.teumteum.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 모든 도메인이 함께 쓰는 공통 오류 코드.
 * 특정 도메인에만 쓰는 오류(COURSE_NOT_FOUND 등)는 여기에 넣지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."), // 400
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."), // 401
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."), // 403
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요."), //404
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요."), // 405
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식이에요."), // 415
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."); // 500

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }


}
