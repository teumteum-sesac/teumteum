package com.teumteum.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.teumteum.global.exception.CustomException;
import com.teumteum.member.domain.Member;
import com.teumteum.member.dto.SignupRequest;
import com.teumteum.member.dto.SignupResponse;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/*
 * [이 파일이 하는 일]
 * MemberService 의 회원가입 규칙(중복이면 막기, 비밀번호 암호화)이 맞게 동작하는지 확인하는 단위 테스트.
 * DB 없이 돈다.
 *
 * [Mock(가짜 객체)을 쓰는 이유]
 *   진짜 MemberRepository 는 DB 가 있어야 동작한다. 여기서는 "Service 의 판단"만 확인하고 싶으므로
 *   Repository 는 Mockito 로 만든 가짜를 넣고, 어떤 값을 돌려줄지 직접 정한다.
 *     given(memberRepository.existsByEmail("a@b.com")).willReturn(true);   // "이미 있다"고 대답하게 함
 *   서비스가 생성자로 의존성을 받기 때문에(의존성 주입) 이렇게 가짜를 끼워 넣을 수 있다.
 *
 * 비밀번호 암호화기는 진짜 BCrypt 를 써서 실제로 해시가 되는지 확인한다.
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.member.service.MemberServiceTest"
 */
@ExtendWith(MockitoExtension.class)   // @Mock 이 붙은 필드를 Mockito 가 가짜 객체로 채워 준다
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberService memberService;

    @BeforeEach   // 각 테스트 실행 전에 매번 새로 만든다
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder);
    }

    @Test
    @DisplayName("가입하면 비밀번호를 BCrypt 해시로 바꿔 저장하고, 응답에는 이메일·닉네임을 담는다")
    void signup() {
        // given: 가입되지 않은 이메일, save 는 받은 회원을 그대로 돌려준다고 가정
        SignupRequest request = new SignupRequest("user@email.com", "abc12345", "틈틈이");
        given(memberRepository.existsByEmail("user@email.com")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignupResponse response = memberService.signup(request);

        // then 1: 응답 값
        assertThat(response.email()).isEqualTo("user@email.com");
        assertThat(response.nickname()).isEqualTo("틈틈이");

        // then 2: 실제로 save() 에 넘어간 회원을 붙잡아서(ArgumentCaptor) 비밀번호가 어떻게 저장됐는지 확인
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isNotEqualTo("abc12345");                       // 원문이 저장되면 안 됨
        assertThat(passwordEncoder.matches("abc12345", saved.getPasswordHash())).isTrue();  // 원래 비밀번호와는 맞아야 함
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 DUPLICATE_EMAIL 예외가 나고 저장하지 않는다")
    void signupDuplicateEmail() {
        // given
        SignupRequest request = new SignupRequest("user@email.com", "abc12345", "틈틈이");
        given(memberRepository.existsByEmail("user@email.com")).willReturn(true);

        // when & then: 예외가 나는지, 그 예외의 ErrorCode 가 DUPLICATE_EMAIL 인지
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);

        verify(memberRepository, never()).save(any());   // save 가 한 번도 불리지 않았는지
    }

    @Test
    @DisplayName("이메일 중복 확인은 Repository 결과를 그대로 돌려준다")
    void existsByEmail() {
        given(memberRepository.existsByEmail("user@email.com")).willReturn(true);
        given(memberRepository.existsByEmail("new@email.com")).willReturn(false);

        assertThat(memberService.existsByEmail("user@email.com")).isTrue();
        assertThat(memberService.existsByEmail("new@email.com")).isFalse();
    }
}
