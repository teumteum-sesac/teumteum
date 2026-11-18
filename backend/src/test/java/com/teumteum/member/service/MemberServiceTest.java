package com.teumteum.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.teumteum.admin.service.AdminService;
import com.teumteum.global.auth.JwtProvider;
import com.teumteum.global.exception.CustomException;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.member.domain.Member;
import com.teumteum.member.dto.LoginRequest;
import com.teumteum.member.dto.LoginResponse;
import com.teumteum.member.dto.SignupRequest;
import com.teumteum.member.dto.MemberUpdateRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.CsvSource;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/*
 * [이 파일이 하는 일]
 * MemberService 의 규칙(가입: 중복 막기·비밀번호 암호화·토큰 발급 / 로그인: 비밀번호 확인·탈퇴 회원 막기)을 확인하는 단위 테스트.
 * DB 없이 돈다.
 *
 * [Mock(가짜 객체)을 쓰는 이유]
 *   진짜 MemberRepository 는 DB 가 있어야 동작한다. 여기서는 "Service 의 판단"만 확인하고 싶으므로
 *   Repository 는 Mockito 로 만든 가짜를 넣고, 어떤 값을 돌려줄지 직접 정한다.
 *     given(memberRepository.existsByEmail("a@b.com")).willReturn(true);   // "이미 있다"고 대답하게 함
 *   서비스가 생성자로 의존성을 받기 때문에(의존성 주입) 이렇게 가짜를 끼워 넣을 수 있다.
 *
 * 비밀번호 암호화기(BCrypt)와 토큰 발급기(JwtProvider)는 진짜를 써서 실제로 해시·토큰이 되는지 확인한다.
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.member.service.MemberServiceTest"
 */
@ExtendWith(MockitoExtension.class)   // @Mock 이 붙은 필드를 Mockito 가 가짜 객체로 채워 준다
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AdminService adminService;   // 관리자 여부: 기본은 false (Mockito 기본값)

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtProvider jwtProvider = new JwtProvider("test-secret-key-for-teumteum-jwt-0123456789", 3600);

    private MemberService memberService;

    @BeforeEach   // 각 테스트 실행 전에 매번 새로 만든다
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder, jwtProvider, adminService);
    }

    /** DB 에 저장된 것처럼 id 가 매겨진 회원을 만든다 (id 는 원래 DB 가 매기므로 테스트에서는 직접 넣는다) */
    private Member savedMember(Long id, String rawPassword) {
        Member member = Member.create("user@email.com", passwordEncoder.encode(rawPassword), "틈틈이");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("본인 정보 조회는 저장된 이메일과 닉네임을 반환한다")
    void getMyInfo() {
        Member member = savedMember(7L, "abc12345");
        given(memberRepository.findById(7L)).willReturn(Optional.of(member));

        var response = memberService.getMyInfo(7L);

        assertThat(response.email()).isEqualTo("user@email.com");
        assertThat(response.nickname()).isEqualTo("틈틈이");
        verify(memberRepository).findById(7L);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("본인 정보 조회에서 회원이 없으면 401로 거절한다")
    void getMyInfoMissingMember() {
        given(memberRepository.findById(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyInfo(7L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("본인 정보 조회에서 탈퇴한 회원은 401로 거절한다")
    void getMyInfoWithdrawnMember() {
        Member member = savedMember(7L, "abc12345");
        member.withdraw();
        given(memberRepository.findById(7L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.getMyInfo(7L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void updatesNicknameAndKeepsPasswordWhenBothPasswordFieldsAreEmpty(String empty) {
        Member member = savedMember(7L, "abc12345");
        String originalHash = member.getPasswordHash();
        given(memberRepository.findById(7L)).willReturn(Optional.of(member));

        var response = memberService.updateMyInfo(7L, new MemberUpdateRequest("새닉네임", empty, empty));

        assertThat(response.email()).isEqualTo("user@email.com");
        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(member.getPasswordHash()).isEqualTo(originalHash);
        assertThat(member.getEmail()).isEqualTo("user@email.com");
    }

    @Test
    void updatesPasswordAsHashAndLoginAcceptsOnlyNewPassword() {
        Member member = savedMember(7L, "abc12345");
        given(memberRepository.findById(7L)).willReturn(Optional.of(member));
        memberService.updateMyInfo(7L, new MemberUpdateRequest("새닉네임", "newpass123", "newpass123"));

        assertThat(member.getPasswordHash()).isNotEqualTo("newpass123");
        assertThat(passwordEncoder.matches("newpass123", member.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("abc12345", member.getPasswordHash())).isFalse();
        given(memberRepository.findByEmail("user@email.com")).willReturn(Optional.of(member));
        assertThat(memberService.login(new LoginRequest("user@email.com", "newpass123")).nickname())
                .isEqualTo("새닉네임");
        assertThatThrownBy(() -> memberService.login(new LoginRequest("user@email.com", "abc12345")))
                .isInstanceOf(CustomException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"short,short", "newpass123,different", "NULL,newpass123", "newpass123,NULL", "'        ','        '"}, nullValues = "NULL")
    void rejectsInvalidPasswordBeforeLookingUpMember(String password, String confirm) {
        assertThatThrownBy(() -> memberService.updateMyInfo(7L,
                new MemberUpdateRequest("새닉네임", password, confirm)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        verify(memberRepository, never()).findById(any());
    }

    @Test
    void rejectsOversizedPasswordsAndNickname() {
        for (var request : java.util.List.of(
                new MemberUpdateRequest("닉네임", "a".repeat(65), "a".repeat(65)),
                new MemberUpdateRequest("닉네임", "가".repeat(25), "가".repeat(25)),
                new MemberUpdateRequest("닉".repeat(51), null, null),
                new MemberUpdateRequest(" ", null, null))) {
            assertThatThrownBy(() -> memberService.updateMyInfo(7L, request))
                    .isInstanceOf(CustomException.class);
        }
        verify(memberRepository, never()).findById(any());
    }

    @Test
    void rejectsUpdateForMissingOrWithdrawnMember() {
        var request = new MemberUpdateRequest("새닉네임", null, null);
        given(memberRepository.findById(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> memberService.updateMyInfo(7L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));

        Member withdrawn = savedMember(8L, "abc12345");
        withdrawn.withdraw();
        given(memberRepository.findById(8L)).willReturn(Optional.of(withdrawn));
        assertThatThrownBy(() -> memberService.updateMyInfo(8L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        assertThat(withdrawn.getNickname()).isEqualTo("틈틈이");
    }

    // ===== 회원가입 =====

    @Test
    @DisplayName("가입하면 비밀번호를 BCrypt 해시로 저장하고, 바로 쓸 수 있는 로그인 토큰을 준다")
    void signup() {
        // given: 가입되지 않은 이메일, save 는 받은 회원에 id 1 을 매겨 돌려준다고 가정
        SignupRequest request = new SignupRequest("user@email.com", "abc12345", "틈틈이");
        given(memberRepository.existsByEmail("user@email.com")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });

        // when
        LoginResponse response = memberService.signup(request);

        // then 1: 응답 값 (가입 후 자동 로그인 → 토큰이 들어 있고, 그 토큰의 주인이 방금 가입한 회원)
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@email.com");
        assertThat(response.nickname()).isEqualTo("틈틈이");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(jwtProvider.getMemberId(response.accessToken())).isEqualTo(1L);

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

    // ===== 로그인 =====

    @Test
    @DisplayName("이메일·비밀번호가 맞으면 그 회원의 토큰을 발급한다")
    void login() {
        given(memberRepository.findByEmail("user@email.com")).willReturn(Optional.of(savedMember(5L, "abc12345")));

        LoginResponse response = memberService.login(new LoginRequest("user@email.com", "abc12345"));

        assertThat(response.memberId()).isEqualTo(5L);
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(jwtProvider.getMemberId(response.accessToken())).isEqualTo(5L);
    }

    @Test
    @DisplayName("없는 이메일이면 LOGIN_FAILED")
    void loginUnknownEmail() {
        given(memberRepository.findByEmail("none@email.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.login(new LoginRequest("none@email.com", "abc12345")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 LOGIN_FAILED (없는 이메일과 같은 오류)")
    void loginWrongPassword() {
        given(memberRepository.findByEmail("user@email.com")).willReturn(Optional.of(savedMember(5L, "abc12345")));

        assertThatThrownBy(() -> memberService.login(new LoginRequest("user@email.com", "wrong-password")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("탈퇴한 회원은 비밀번호가 맞아도 LOGIN_FAILED")
    void loginWithdrawnMember() {
        Member withdrawn = savedMember(5L, "abc12345");
        withdrawn.withdraw();
        given(memberRepository.findByEmail("user@email.com")).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> memberService.login(new LoginRequest("user@email.com", "abc12345")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("관리자 회원이 로그인하면 토큰과 응답에 관리자 표시가 들어간다 (REQ-044)")
    void loginAdmin() {
        given(memberRepository.findByEmail("user@email.com")).willReturn(Optional.of(savedMember(5L, "abc12345")));
        given(adminService.isAdmin(5L)).willReturn(true);

        LoginResponse response = memberService.login(new LoginRequest("user@email.com", "abc12345"));

        assertThat(response.admin()).isTrue();
        assertThat(jwtProvider.parse(response.accessToken()).admin()).isTrue();
    }

    @Test
    @DisplayName("일반 회원 토큰에는 관리자 표시가 없다")
    void loginNormalMember() {
        given(memberRepository.findByEmail("user@email.com")).willReturn(Optional.of(savedMember(5L, "abc12345")));

        LoginResponse response = memberService.login(new LoginRequest("user@email.com", "abc12345"));

        assertThat(response.admin()).isFalse();
        assertThat(jwtProvider.parse(response.accessToken()).admin()).isFalse();
    }
}
