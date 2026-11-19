package com.teumteum.global.config;

import com.teumteum.global.auth.LoginMemberArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * [이 파일이 하는 일]
 * Spring MVC(컨트롤러 동작 방식)에 우리 설정을 더하는 곳.
 * 지금은 @LoginMember 를 처리하는 LoginMemberArgumentResolver 를 등록한다.
 * 등록하지 않으면 Spring 이 @LoginMember 를 모르고, 매개변수를 채우지 못해 오류가 난다.
 *
 * WebMvcConfigurer 를 구현하면, 필요한 메서드만 골라 덮어써서 설정을 추가할 수 있다.
 * 나중에 CORS·인터셉터 같은 웹 설정이 필요하면 여기에 추가한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginMemberArgumentResolver loginMemberArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
    }
}
