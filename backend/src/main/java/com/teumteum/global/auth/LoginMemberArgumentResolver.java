package com.teumteum.global.auth;

import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import org.springframework.core.MethodParameter;
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
 *   우리는 "@LoginMember 는 회원 ID 로 채운다"는 부품을 하나 더 만든 것이다.
 *   만들기만 하면 안 되고 global/config/WebConfig 에 등록해야 Spring 이 쓴다.
 *
 * [동작 순서]
 *   1) supportsParameter : 이 매개변수가 내가 처리할 대상인가? (@LoginMember + Long 타입)
 *   2) resolveArgument   : 그렇다면 무슨 값을 넣을까? → 회원 ID
 *
 * TODO(김용현): JWT 로그인 완성 후 resolveArgument 를 "인증된 사용자(토큰)에서 회원 ID 꺼내기"로 교체하고 X-Member-Id 임시 방식 삭제.
 *   임시 방식은 누구나 헤더로 다른 회원 ID 를 넣을 수 있어서 개발·테스트용으로만 쓴다. 배포 전에 반드시 교체.
 */
@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    /** [임시] 회원 ID 를 담아 보내는 요청 헤더 이름 */
    public static final String MEMBER_ID_HEADER = "X-Member-Id";

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
        String header = webRequest.getHeader(MEMBER_ID_HEADER);

        // 헤더가 없으면 = 로그인 안 한 요청 → 401 "로그인이 필요해요." (GlobalExceptionHandler 가 공통 응답으로 바꿈)
        if (header == null || header.isBlank()) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }

        // 숫자가 아니거나 1보다 작으면 올바른 회원 ID 가 아니므로 역시 401
        try {
            long memberId = Long.parseLong(header.trim());
            if (memberId < 1) {
                throw new CustomException(CommonErrorCode.UNAUTHORIZED);
            }
            return memberId;
        } catch (NumberFormatException e) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
