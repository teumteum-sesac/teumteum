package com.teumteum.member.dto;

import com.teumteum.member.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * [이 파일이 하는 일]
 * 회원가입 요청으로 프론트가 보내는 데이터(JSON)를 담는 그릇 = 요청 DTO.
 *   POST /api/members
 *   { "email": "user@email.com", "password": "abc12345", "nickname": "틈틈이" }
 *   → Spring 이 이 JSON 을 SignupRequest 객체로 자동 변환해 컨트롤러에 넘긴다.
 *
 * [DTO 를 따로 만드는 이유] (AGENTS.md 5장: 요청은 XxxRequest)
 *   Member 엔티티를 그대로 받으면, 프론트가 accountStatus 같은 값까지 마음대로 보낼 수 있다.
 *   DTO 는 "가입할 때 받을 값"만 딱 정해 둔다.
 *
 * [입력 검증 어노테이션]
 *   컨트롤러에서 @Valid 를 붙이면 아래 규칙을 먼저 검사하고, 어기면 컨트롤러에 들어오기 전에
 *   GlobalExceptionHandler 가 400 INVALID_REQUEST + 어느 칸이 왜 틀렸는지(fieldErrors)로 응답한다. (NFR-006 서버 측 검증)
 *   프론트(SignupForm)도 같은 검사를 하지만, 프론트 검사는 우회할 수 있어서 서버에서 한 번 더 한다.
 *
 * [record 란]
 *   값만 담는 클래스를 짧게 쓰는 문법. 생성자·getter(email(), password() …)를 자동으로 만들고, 값은 바꿀 수 없다.
 *   혜빈님 PlaceVisit, 종일님 ApiResponse 도 record 다.
 *
 * 화면 기준: 화면설계서 AUT-02 / 요구사항 REQ-001
 *   약관 동의는 프론트에서 체크해야 가입 버튼이 눌리므로 서버로는 보내지 않는다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식을 확인해 주세요.")
        @Size(max = Member.EMAIL_MAX_LENGTH, message = "이메일이 너무 길어요.")
        String email,

        // 8자 이상 (화면설계서 AUT-02). 최대 64자: BCrypt 는 앞 72바이트까지만 쓰므로 그보다 짧게 제한한다
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = Member.NICKNAME_MAX_LENGTH, message = "닉네임은 50자 이하로 입력해 주세요.")
        String nickname
) {
}
