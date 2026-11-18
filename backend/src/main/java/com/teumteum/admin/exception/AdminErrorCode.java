package com.teumteum.admin.exception;

import com.teumteum.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/*
 * [이 파일이 하는 일]
 * 관리자·공지사항 기능에서만 쓰는 오류 목록. throw new CustomException(AdminErrorCode.XXX) 로 던지면
 * GlobalExceptionHandler 가 공통 실패 응답으로 바꾼다.
 * (관리자가 아닌 사람이 /api/admin/** 에 오면 403 FORBIDDEN 은 SecurityConfig 가 먼저 막는다)
 */
@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없어요."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없어요."),
    /** 관리자 자신을 탈퇴시키면 관리자 화면에 다시 들어올 수 없게 된다 */
    CANNOT_WITHDRAW_SELF(HttpStatus.BAD_REQUEST, "본인 계정은 여기서 탈퇴 처리할 수 없어요."),
    /** 다른 관리자는 강제 탈퇴하지 않는다 (관리자 해제는 DB 에서) */
    CANNOT_WITHDRAW_ADMIN(HttpStatus.BAD_REQUEST, "관리자 계정은 탈퇴 처리할 수 없어요."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "이미 탈퇴한 회원이에요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
