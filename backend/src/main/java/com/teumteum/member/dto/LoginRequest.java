package com.teumteum.member.dto;

import jakarta.validation.constraints.NotBlank;

/*
 * [이 파일이 하는 일]
 * 로그인 요청 데이터 (화면 AUT-01).
 *   POST /api/auth/login
 *   { "email": "user@email.com", "password": "abc12345" }
 *
 * 회원가입(SignupRequest)과 달리 형식·길이 검사는 하지 않고 "비어 있는지"만 본다.
 * 형식이 틀린 이메일이면 어차피 회원을 못 찾아서 "이메일 또는 비밀번호가 맞지 않아요"로 끝나기 때문이다.
 * (어떤 칸이 왜 틀렸는지 자세히 알려 주면 오히려 공격자에게 힌트가 된다)
 */
public record LoginRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}
