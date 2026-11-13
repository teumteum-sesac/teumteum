package com.teumteum.global.exception;

import lombok.Getter;

/**
 * 비즈니스 오류를 알릴 때 던지는 예외. GlobalExceptionHandler 가 ErrorCode 에 맞는 응답으로 바꾼다.
 * <pre>
 * throw new CustomException(CommonErrorCode.INVALID_REQUEST);
 * </pre>
 * 컨트롤러에서 try-catch 로 잡아 응답을 만들지 않는다. (AGENTS.md 5장)
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 원인 예외를 로그에 남기고 싶을 때 (예: 외부 API 호출 실패). 응답에는 원인이 노출되지 않는다
     */
    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
