package com.teumteum.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.exception.GlobalExceptionHandler;
import com.teumteum.member.dto.LoginRequest;
import com.teumteum.member.dto.LoginResponse;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/*
 * [이 파일이 하는 일]
 * AuthController(POST /api/auth/login)가 성공·실패·입력 누락에 맞는 상태 코드와 JSON 으로 응답하는지 확인한다.
 * Service 는 가짜(mock)로 바꾸고 컨트롤러의 요청·응답만 본다. (로그인 판단 로직은 MemberServiceTest 에서 확인)
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.member.controller.AuthControllerTest"
 */
class AuthControllerTest {

    private MockMvc mockMvc;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("로그인 성공 → 200, data 에 토큰과 회원 정보")
    void login() throws Exception {
        given(memberService.login(any(LoginRequest.class)))
                .willReturn(new LoginResponse("token-value", "Bearer", 3600, 1L, "user@email.com", "틈틈이"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@email.com", "password": "abc12345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("token-value"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.nickname").value("틈틈이"));
    }

    @Test
    @DisplayName("이메일·비밀번호가 틀리면 → 401 LOGIN_FAILED")
    void loginFailed() throws Exception {
        given(memberService.login(any(LoginRequest.class)))
                .willThrow(new CustomException(MemberErrorCode.LOGIN_FAILED));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@email.com", "password": "wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 맞지 않아요."));
    }

    @Test
    @DisplayName("이메일·비밀번호를 비워 보내면 → 400 INVALID_REQUEST + 두 칸 모두 안내")
    void loginBlank() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "", "password": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2));
    }
}
