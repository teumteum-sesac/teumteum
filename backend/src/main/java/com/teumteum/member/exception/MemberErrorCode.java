package com.teumteum.member.exception;

import com.teumteum.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/*
 * [이 파일이 하는 일]
 * 회원(member) 도메인에서만 쓰는 오류 목록.
 * 정종일님이 만든 공통 규약(global/exception/ErrorCode)을 구현해서, 던지면 공통 응답 형식으로 자동 변환된다.
 *
 * [쓰는 법]
 *   throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
 *   → GlobalExceptionHandler 가 잡아서 아래 응답으로 바꿔 준다
 *     HTTP 409
 *     { "success": false, "data": null,
 *       "error": { "code": "DUPLICATE_EMAIL", "message": "이미 가입된 이메일이에요." } }
 *
 * 프론트는 error.code 로 분기하고, error.message 는 화면에 그대로 보여 준다.
 * 회원 오류가 더 필요하면(예: 로그인 실패, 회원 없음) 여기에 한 줄씩 추가한다.
 */
@Getter                    // getStatus(), getMessage() 를 자동으로 만들어 ErrorCode 의 메서드를 채운다
@RequiredArgsConstructor   // final 필드(status, message)를 받는 생성자를 자동으로 만든다
public enum MemberErrorCode implements ErrorCode {

    /** 409 Conflict: 요청은 맞지만 이미 있는 데이터와 "충돌"할 때 쓰는 상태 코드 */
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),

    /**
     * 401 Unauthorized: 로그인 실패.
     * 이메일이 없는 경우·비밀번호가 틀린 경우·탈퇴한 회원을 모두 같은 문구로 응답한다.
     * 구분해서 알려 주면 "이 이메일은 가입돼 있다"는 정보가 새어 나가기 때문이다.
     */
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),

    WITHDRAWAL_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 맞지 않아요.");

    private final HttpStatus status;
    private final String message;

    /** 코드 문자열은 enum 이름 그대로 ("DUPLICATE_EMAIL"). CommonErrorCode 와 같은 방식 */
    @Override
    public String getCode() {
        return name();
    }
}
