package com.teumteum.global.auth;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * [이 파일이 하는 일]
 * "지금 요청을 보낸 로그인 회원의 ID 를 여기에 넣어 달라"는 표시(어노테이션).
 * 회원 전용 API(코스 저장, 마이페이지, 관리자 등) 컨트롤러에서 쓴다.
 *
 * [쓰는 법] — 팀원 전원 공통
 *   @PostMapping("/api/courses")
 *   public ResponseEntity<ApiResponse<CourseSaveResponse>> save(
 *           @LoginMember Long memberId,                  // ← 로그인한 회원 ID 가 자동으로 들어온다
 *           @Valid @RequestBody CourseSaveRequest request) { ... }
 *
 * [회원 ID 는 어디서 오나]
 *   프론트가 보낸 Authorization: Bearer {토큰} 을 JwtAuthenticationFilter 가 검증하고,
 *   LoginMemberArgumentResolver 가 그 인증 정보에서 회원 ID 를 꺼내 넣어 준다.
 *   토큰이 없거나 만료·위조면 401 UNAUTHORIZED("로그인이 필요해요.")로 응답한다.
 *   회원 전용 경로는 global/config/SecurityConfig 에 등록한다.
 *
 * [테스트할 때]
 *   Swagger·Postman : POST /api/auth/login 으로 받은 accessToken 을 Authorization: Bearer {토큰} 헤더로 보낸다.
 *   MockMvc        : .principal(new UsernamePasswordAuthenticationToken(1L, null, List.of())) 를 붙인다.
 *
 * [어노테이션 위 설정들]
 *   @Target(PARAMETER)      : 메서드 매개변수에만 붙일 수 있다
 *   @Retention(RUNTIME)     : 서버 실행 중에도 이 표시를 읽을 수 있게 남겨 둔다 (Spring 이 읽어야 하므로)
 *   @Parameter(hidden=true) : Swagger 화면에 memberId 를 입력칸으로 보여 주지 않는다 (사용자가 입력하는 값이 아니므로)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface LoginMember {
}
