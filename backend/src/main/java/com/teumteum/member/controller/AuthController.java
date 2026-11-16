package com.teumteum.member.controller;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.member.dto.LoginRequest;
import com.teumteum.member.dto.LoginResponse;
import com.teumteum.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * [이 파일이 하는 일]
 * 인증(로그인) 요청을 받는 입구.
 *   POST /api/auth/login   로그인 → JWT 발급 (REQ-002, 화면 AUT-01)
 *
 * [왜 MemberController 와 나눴나]
 *   /api/members 는 "회원이라는 자원"(가입·조회·수정)을 다루고,
 *   로그인은 자원을 만드는 게 아니라 "인증 행위"라서 /api/auth 로 따로 둔다.
 *   나중에 토큰 재발급(/api/auth/refresh), 비밀번호 찾기(REQ-054)도 여기에 추가한다.
 *
 * [로그아웃은 API 가 없다] (REQ-003)
 *   JWT 는 서버가 로그인 상태를 저장하지 않는 방식이라, 프론트가 보관한 토큰을 지우면 로그아웃이다 (authStore.logout).
 *
 * 응답 예 (200):
 *   { "success": true, "data": { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600,
 *                                 "memberId": 1, "email": "user@email.com", "nickname": "틈틈이" }, "error": null }
 * 실패 (401): { "success": false, "data": null, "error": { "code": "LOGIN_FAILED", "message": "이메일 또는 비밀번호가 맞지 않아요." } }
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.login(request)));
    }
}
