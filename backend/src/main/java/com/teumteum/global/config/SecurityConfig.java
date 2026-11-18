package com.teumteum.global.config;

import com.teumteum.global.auth.JwtAuthenticationFilter;
import com.teumteum.global.auth.JwtProvider;
import com.teumteum.global.auth.SecurityErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/*
 * [이 파일이 하는 일]
 * "어떤 주소는 로그인해야 들어갈 수 있는가"를 정하는 보안 설정 (NFR-002).
 *
 * [경로 규칙] — 서비스는 비회원 이용 중심 (AGENTS.md 1장)
 *   로그인 필요 : /api/courses/**     코스 저장·조회·삭제 (MY-02·MY-03, DET-01 저장)
 *                 /api/members/me/**  내 정보 조회·수정·탈퇴 (MY-01·MY-04)
 *   관리자만    : /api/admin/**       로그인 + admin 테이블에 있는 회원 (토큰의 role=ADMIN). 아니면 403 (REQ-044)
 *   나머지 전부 : 로그인 없이 허용 (추천·장소·회원가입·로그인·Swagger 등)
 *   새 회원 전용 API 를 만들면 위 목록에 경로를 추가한다.
 *
 * [요청 한 번의 흐름]
 *   JwtAuthenticationFilter (토큰 → 회원 ID 인증 정보 등록)
 *     → authorizeHttpRequests (경로 규칙 검사, 막히면 SecurityErrorHandler 가 401/403 공통 응답)
 *     → 컨트롤러 (@LoginMember 로 회원 ID 받기)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final SecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)         // JWT(무상태) 방식이라 CSRF 불필요
                .formLogin(AbstractHttpConfigurer::disable)    // 기본 로그인 화면 끄기
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 서버 세션 안 씀
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")   // 관리자만 (일반 회원은 403)
                        .requestMatchers("/api/courses/**", "/api/members/me/**").authenticated()
                        .anyRequest().permitAll()
                )
                // 401·403 을 공통 응답(ApiResponse) 형식으로
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )
                // Spring 기본 로그인 필터보다 앞에서 토큰을 확인한다
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
