package com.teumteum.global.auth;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.member.service.MemberService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/*
 * [이 파일이 하는 일]
 * 모든 요청이 컨트롤러에 닿기 전에 한 번 거치는 "검문소".
 * 요청 헤더 Authorization: Bearer {토큰} 이 있으면 토큰을 검증하고,
 * 진짜 토큰이면 "이 요청은 회원 N번이 보냈다"는 인증 정보를 Spring Security 에 등록한다.
 *
 * [흐름]
 *   프론트(client.ts)가 로그인 후 받은 토큰을 매 요청 헤더에 붙인다
 *     → 이 필터가 토큰에서 회원 ID 를 꺼낸다 (JwtProvider.getMemberId: 위조·만료면 실패)
 *     → SecurityContext 에 인증 정보 저장
 *     → SecurityConfig 가 회원 전용 경로를 통과시킬지 결정
 *     → 컨트롤러의 @LoginMember Long memberId 에 회원 ID 가 들어간다 (LoginMemberArgumentResolver)
 *
 * [토큰이 없거나 틀리면?]
 *   여기서 바로 막지 않고 "인증 안 된 요청"으로 그냥 넘긴다.
 *   로그인 없이 쓰는 API(추천·장소 검색 등)는 토큰이 만료돼 있어도 계속 써야 하기 때문이다.
 *   회원 전용 경로라면 다음 단계(SecurityConfig)에서 401 로 막힌다.
 *
 * [@Component 를 붙이지 않는 이유]
 *   붙이면 Spring Boot 가 이 필터를 일반 서블릿 필터로도 한 번 더 등록해서 두 번 실행된다.
 *   SecurityConfig 에서 직접 만들어 보안 필터 체인에만 넣는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** Spring Security 권한 이름 규칙: hasRole("ADMIN") 은 "ROLE_ADMIN" 권한을 찾는다 */
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final JwtProvider jwtProvider;
    private final MemberService memberService;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, MemberService memberService) {
        this.jwtProvider = jwtProvider;
        this.memberService = memberService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                JwtProvider.TokenClaims claims = jwtProvider.parse(token);
                // 탈퇴 후 기존 토큰의 서명·만료가 유효해도 회원 전용 경로에 접근할 수 없게 한다.
                if (!memberService.isActiveMember(claims.memberId())) {
                    throw new CustomException(CommonErrorCode.UNAUTHORIZED);
                }
                // principal(누구인가) = 회원 ID, 비밀번호 자리 = null, 권한 = 관리자면 ROLE_ADMIN (SecurityConfig 의 hasRole("ADMIN"))
                List<SimpleGrantedAuthority> authorities = claims.admin() ? List.of(new SimpleGrantedAuthority(ROLE_ADMIN)) : List.of();
                var authentication = new UsernamePasswordAuthenticationToken(claims.memberId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (CustomException e) {
                // 위조·만료·형식 오류: 인증 정보 없이 진행 (회원 전용 경로면 SecurityConfig 가 401 로 막는다)
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /** "Bearer abc.def.ghi" → "abc.def.ghi". 헤더가 없거나 Bearer 형식이 아니면 null */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
