package com.teumteum.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/*
 * [이 파일이 하는 일]
 * 비밀번호 암호화기(BCrypt)를 Spring 에 "Bean" 으로 등록한다. (NFR-001 비밀번호 암호화)
 *
 * [Bean 이 뭔가]
 *   Spring 이 대신 만들어서 보관해 두는 객체. 필요한 곳에서 "PasswordEncoder 주세요" 하면 이 객체를 넣어 준다(의존성 주입).
 *   → 회원가입(MemberService), 비밀번호 변경(김하윤), 로그인 비밀번호 확인이 모두 이 객체 하나를 같이 쓴다.
 *
 * [쓰는 법]
 *   passwordEncoder.encode("abc123")              → "$2a$10$..." (저장할 해시값, 매번 솔트가 달라 결과도 다름)
 *   passwordEncoder.matches("abc123", 저장된해시)   → true / false (로그인 때 비교)
 *
 * [왜 SecurityConfig 가 아니라 따로 두나]
 *   SecurityConfig 는 "어느 주소를 막을지" 담당이고, 암호화기는 member 쪽에서도 쓰는 도구라 역할을 나눴다.
 */
@Configuration   // 이 클래스 안의 @Bean 메서드들을 Spring 이 읽어서 객체를 등록한다
public class PasswordEncoderConfig {

    // @Bean 메서드는 Spring 이 직접 호출하므로 public 이 없어도 된다
    @Bean
    PasswordEncoder passwordEncoder() {
        // 반환 타입을 BCryptPasswordEncoder 가 아니라 인터페이스(PasswordEncoder)로 둔다.
        // 쓰는 쪽은 "암호화기"라는 역할만 알면 되고, 나중에 알고리즘을 바꿔도 쓰는 코드는 안 바뀐다.
        return new BCryptPasswordEncoder();
    }
}
