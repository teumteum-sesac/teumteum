package com.teumteum.course.exception;

import com.teumteum.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 코스 관련 오류의 종류를 정의한다.
@Getter
@RequiredArgsConstructor
public enum CourseErrorCode implements ErrorCode {

    // 코스가 없거나 다른 회원 소유이면 404로 처리한다.
    COURSE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COURSE_NOT_FOUND",
            "저장한 코스를 찾을 수 없어요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}