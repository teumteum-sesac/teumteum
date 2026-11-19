package com.teumteum.global.auth;

import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import java.security.Principal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/*
 * [이 파일이 하는 일]
 * 컨트롤러 매개변수에 @LoginMember 가 붙어 있으면, 로그인한 회원 ID 를 찾아서 그 자리에 넣어 준다.
 *
 * [ArgumentResolver 란]
 *   Spring 이 컨트롤러 메서드를 부르기 전에 "매개변수에 뭘 넣을지" 결정하는 부품.
 *   @RequestBody, @RequestParam 도 Spring 안에 이런 부품이 있어서 동작한다.
 *   우리는 "@LoginMember 는 회원 ID 로 채운다"는 부품을 하나 더 만든 것이다. (global/config/WebConfig 에 등록)
 *
 * [회원 ID 는 어디서 오나] — JWT 방식
 *   JwtAuthenticationFilter 가 토큰을 검증하고 인증 정보(Authentication, principal = 회원 ID)를 등록해 둔다.
 *   Spring Security 는 그 인증 정보를 request.getUserPrincipal() 로도 꺼낼 수 있게 해 준다.
 *   여기서는 그것을 꺼내 Long 회원 ID 로 돌려준다.
 *   → 예전 임시 방식(X-Member-Id 헤더)은 누구나 다른 회원 ID 를 넣을 수 있어서 삭제했다.
 *
 * [테스트에서는]
 *   MockMvc 요청에 .principal(new UsernamePasswordAuthenticationToken(7L, null, List.of())) 를 붙이면
 *   로그인한 회원 7번이 보낸 요청이 된다. (CourseControllerTest 참고)
 */
@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Principal principal = webRequest.getUserPrincipal();

        // 토큰으로 인증된 요청이면 principal 이 Authentication 이고, 그 안의 principal 값이 회원 ID(Long) 이다
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof Long memberId
                && memberId > 0) {
            return memberId;
        }

        // 로그인 안 한 요청 → 401 "로그인이 필요해요." (GlobalExceptionHandler 가 공통 응답으로 바꿈)
        // 보통은 SecurityConfig 가 먼저 막지만, 회원 전용 경로 목록에 빠진 API 에서도 안전하게 401 을 낸다
        throw new CustomException(CommonErrorCode.UNAUTHORIZED);
    }
}
