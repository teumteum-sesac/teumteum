package com.teumteum.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyLong;
import com.teumteum.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/*
 * [이 파일이 하는 일]
 * JwtAuthenticationFilter 만 따로 떼어, 토큰에 따라 인증 정보가 등록되는지/안 되는지 확인한다.
 * 어떤 경우에도 요청은 다음 단계로 넘어가야 한다 (막는 건 SecurityConfig 의 몫).
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.global.auth.JwtAuthenticationFilterTest"
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-teumteum-jwt-0123456789";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 3600);
    private final MemberService memberService = mock(MemberService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, memberService);

    @BeforeEach
    void activeMembers() {
        given(memberService.isActiveMember(anyLong())).willReturn(true);
    }

    @Test
    void withdrawnOrMissingMemberIsNotAuthenticatedEvenWithValidToken() throws Exception {
        given(memberService.isActiveMember(5L)).willReturn(false);
        assertThat(runFilter("Bearer " + jwtProvider.createAccessToken(5L))).isNull();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext(); // 테스트끼리 인증 정보가 섞이지 않게
    }

    /** 헤더를 붙여 필터를 한 번 통과시키고, 다음 단계로 넘어갔는지 확인한 뒤 등록된 인증 정보를 돌려준다 */
    private Authentication runFilter(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses");
        if (authorizationHeader != null) request.addHeader("Authorization", authorizationHeader);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("다음 단계로 넘어가야 한다").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("진짜 토큰이면 principal = 회원 ID 인 인증 정보가 등록된다")
    void validToken() throws Exception {
        Authentication auth = runFilter("Bearer " + jwtProvider.createAccessToken(5L));

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(5L);
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("헤더가 없으면 인증 정보 없음")
    void noHeader() throws Exception {
        assertThat(runFilter(null)).isNull();
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 인증 정보 없음")
    void notBearer() throws Exception {
        assertThat(runFilter("Basic abc")).isNull();
        assertThat(runFilter("Bearer ")).isNull();
    }

    @Test
    @DisplayName("위조·엉터리 토큰이면 인증 정보 없음 (예외로 요청이 끊기지 않는다)")
    void invalidToken() throws Exception {
        String forged = new JwtProvider("another-secret-key-for-forgery-0123456789", 3600).createAccessToken(5L);

        assertThat(runFilter("Bearer " + forged)).isNull();
        assertThat(runFilter("Bearer not-a-jwt")).isNull();
    }

    @Test
    @DisplayName("관리자 토큰이면 ROLE_ADMIN 권한이 붙는다")
    void adminToken() throws Exception {
        Authentication auth = runFilter("Bearer " + jwtProvider.createAccessToken(1L, true));

        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(runFilter("Bearer " + jwtProvider.createAccessToken(2L)).getAuthorities()).isEmpty();
    }
}
