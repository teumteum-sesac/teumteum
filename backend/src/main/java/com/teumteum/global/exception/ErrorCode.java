package com.teumteum.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 공통 규약.
 * 공통 오류는 {@link CommonErrorCode}, 도메인 오류는 각 도메인에서 이 인터페이스를 구현한 enum 으로 만든다.
 * 예) course/exception/CourseErrorCode.COURSE_NOT_FOUND
 */
public interface ErrorCode {

    /** 응답 HTTP 상태 */
    HttpStatus getStatus();

    /** 프론트가 분기에 쓰는 코드 (예: "INVALID_REQUEST") */
    String getCode();

    /** 화면에 그대로 보여줄 안내 문구. */
    String getMessage();
}
