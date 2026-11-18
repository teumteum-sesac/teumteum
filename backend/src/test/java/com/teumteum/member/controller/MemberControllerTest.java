package com.teumteum.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.auth.LoginMemberArgumentResolver;
import com.teumteum.global.exception.GlobalExceptionHandler;
import com.teumteum.member.dto.SignupRequest;
import com.teumteum.member.dto.LoginResponse;
import com.teumteum.member.dto.MemberResponse;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/*
 * [이 파일이 하는 일]
 * MemberController 가 HTTP 요청을 받아 올바른 상태 코드·JSON 으로 응답하는지 확인하는 테스트.
 *   - 주소(URL)·메서드(POST/GET)가 맞는지
 *   - 입력 검증(@Valid)이 잘못된 값을 400 으로 막는지
 *   - 응답이 공통 형식 { success, data, error } 인지
 *
 * [MockMvc]
 *   진짜 서버를 켜지 않고, HTTP 요청을 흉내 내서 컨트롤러를 호출하는 테스트 도구.
 *   종일님 GlobalExceptionHandlerTest 와 같은 방식(standaloneSetup)으로 만들었다.
 *   Service 는 가짜(mock)로 바꿔서 "컨트롤러가 요청·응답을 제대로 다루는지"만 본다. (Service 로직은 MemberServiceTest 에서 확인)
 *
 * [jsonPath 읽는 법]  "$.data.email" = 응답 JSON 의 data 안의 email 값
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.member.controller.MemberControllerTest"
 */
class MemberControllerTest {

    private MockMvc mockMvc;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService))
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())   // 예외 → 공통 응답 변환도 실제처럼 붙인다
                .build();
    }

    @Test
    @DisplayName("본인 정보 조회는 인증된 회원 ID를 사용하고 이메일·닉네임만 반환한다")
    void getMyInfo() throws Exception {
        given(memberService.getMyInfo(7L))
                .willReturn(new MemberResponse("user@email.com", "틈틈이"));

        mockMvc.perform(get("/api/members/me")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null, List.of()))
                        .param("memberId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@email.com"))
                .andExpect(jsonPath("$.data.nickname").value("틈틈이"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(memberService).getMyInfo(7L);
    }

    @Test
    @DisplayName("본인 정보 조회에 인증이 없으면 401이며 서비스를 호출하지 않는다")
    void getMyInfoWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("존재하지 않거나 탈퇴한 회원의 조회 오류를 공통 응답으로 반환한다")
    void getMyInfoUnavailableMember() throws Exception {
        given(memberService.getMyInfo(7L))
                .willThrow(new CustomException(CommonErrorCode.UNAUTHORIZED));
        mockMvc.perform(get("/api/members/me")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null, List.of())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("회원가입 성공 → 201, data 에 토큰·회원 정보, 비밀번호는 응답에 없음")
    void signup() throws Exception {
        given(memberService.signup(any(SignupRequest.class)))
                .willReturn(new LoginResponse("token-value", "Bearer", 3600, 1L, "user@email.com", "틈틈이", false));

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@email.com", "password": "abc12345", "nickname": "틈틈이"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("token-value"))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.email").value("user@email.com"))
                .andExpect(jsonPath("$.data.nickname").value("틈틈이"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("이메일 형식이 틀리고 비밀번호가 8자 미만이면 → 400 INVALID_REQUEST + 어느 칸이 틀렸는지")
    void signupInvalidInput() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-email", "password": "short", "nickname": "틈틈이"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2));
    }

    @Test
    @DisplayName("이미 가입된 이메일 → 409 DUPLICATE_EMAIL")
    void signupDuplicateEmail() throws Exception {
        given(memberService.signup(any(SignupRequest.class)))
                .willThrow(new CustomException(MemberErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@email.com", "password": "abc12345", "nickname": "틈틈이"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.error.message").value("이미 가입된 이메일이에요."));
    }

    @Test
    @DisplayName("이메일 중복 확인 → 200, data.exists")
    void checkEmailExists() throws Exception {
        given(memberService.existsByEmail("user@email.com")).willReturn(true);

        mockMvc.perform(get("/api/members/email-exists").param("email", "user@email.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exists").value(true));
    }

    @Test
    @DisplayName("이메일 중복 확인에 이메일 형식이 아니면 → 400")
    void checkEmailExistsInvalid() throws Exception {
        mockMvc.perform(get("/api/members/email-exists").param("email", "not-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
