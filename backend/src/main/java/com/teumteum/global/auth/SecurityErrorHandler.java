package com.teumteum.global.auth;

import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/*
 * [이 파일이 하는 일]
 * Spring Security 가 요청을 막았을 때의 응답을 우리 공통 응답(ApiResponse) 형식으로 만든다.
 *   - 로그인 안 함(토큰 없음·만료·위조)으로 회원 전용 경로 접근 → 401 UNAUTHORIZED "로그인이 필요해요."
 *   - 로그인은 했지만 권한 없음(예: 일반 회원이 관리자 API) → 403 FORBIDDEN "접근 권한이 없어요."
 *
 * [왜 HandlerExceptionResolver 에 넘기나]
 *   보안 필터는 컨트롤러보다 앞에서 동작해서, 여기서 예외를 던지면 GlobalExceptionHandler 가 받지 못한다.
 *   Spring MVC 의 예외 처리 창구(handlerExceptionResolver)에 CustomException 을 직접 넘기면
 *   GlobalExceptionHandler 가 컨트롤러 예외와 똑같이 처리 → 응답 JSON 모양이 한 곳에서 관리된다.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public SecurityErrorHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /** 401: 인증 정보가 없는 요청이 회원 전용 경로에 들어옴 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        resolver.resolveException(request, response, null, new CustomException(CommonErrorCode.UNAUTHORIZED));
    }

    /** 403: 인증은 됐지만 그 경로에 필요한 권한이 없음 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, new CustomException(CommonErrorCode.FORBIDDEN));
    }
}
