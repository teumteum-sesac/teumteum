package com.teumteum.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.teumteum.admin.service.AdminService;
import com.teumteum.course.service.CourseService;
import com.teumteum.member.domain.Member;
import com.teumteum.member.dto.MemberWithdrawRequest;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.repository.MemberRepository;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class MemberWithdrawalServiceTest {
    private final MemberRepository repository = mock(MemberRepository.class);
    private final CourseService courses = mock(CourseService.class);
    private final AdminService admins = mock(AdminService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final MemberWithdrawalService service = new MemberWithdrawalService(repository, courses, encoder, admins);
    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.create("withdraw@example.com", encoder.encode("abc12345"), "탈퇴테스트");
        ReflectionTestUtils.setField(member, "id", 7L);
    }

    @Test
    void withdrawsWithConsentAndPasswordAndDeletesOnlyAuthenticatedMembersCourses() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        assertThat(service.withdrawMyAccount(7L, new MemberWithdrawRequest("abc12345", true)).withdrawn()).isTrue();
        assertThat(member.isActive()).isFalse();
        assertThat(member.getEmail()).isEqualTo("withdrawn+7@teumteum.invalid");
        verify(courses).deleteMemberCourses(7L);
        verify(repository, never()).delete(any());
    }

    // 예전에는 agreed=true 만으로 비밀번호 없이 탈퇴됐다. 토큰만 있으면 계정이 지워지는 구멍이라 막는다.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsConsentWithoutPassword(String password) {
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest(password, true)))
                .isInstanceOfSatisfying(CustomException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        verifyNoInteractions(repository, courses);
    }

    @Test
    void rejectsPasswordWithoutConsent() {
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest("abc12345", false)))
                .isInstanceOfSatisfying(CustomException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        verifyNoInteractions(repository, courses);
    }

    @Test
    void rejectsWrongPasswordBeforeDeletingCourses() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest("wrong", true)))
                .isInstanceOfSatisfying(CustomException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.WITHDRAWAL_PASSWORD_MISMATCH));
        assertThat(member.isActive()).isTrue();
        verifyNoInteractions(courses);
    }

    @Test
    void rejectsAdminSelfWithdrawal() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(admins.isAdmin(7L)).willReturn(true);
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest("abc12345", true)))
                .isInstanceOfSatisfying(CustomException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.ADMIN_WITHDRAWAL_NOT_ALLOWED));
        assertThat(member.isActive()).isTrue();
        verifyNoInteractions(courses);
    }

    @Test
    void rejectsMissingOrAlreadyWithdrawnMember() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest("abc12345", true)))
                .isInstanceOfSatisfying(CustomException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        member.withdraw();
        given(repository.findByIdForUpdate(8L)).willReturn(Optional.of(member));
        assertThatThrownBy(() -> service.withdrawMyAccount(8L, new MemberWithdrawRequest("abc12345", true)))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(courses);
    }

    @Test
    void doesNotChangeStatusWhenCourseDeletionFails() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        doThrow(new IllegalStateException("test failure")).when(courses).deleteMemberCourses(7L);
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest("abc12345", true)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(member.isActive()).isTrue();
        assertThat(member.getEmail()).isEqualTo("withdraw@example.com");
    }

    @Test
    void withdrawLockedMemberDeletesCoursesForAdminPath() {
        Member result = service.withdrawLockedMember(member);
        assertThat(result.isActive()).isFalse();
        verify(courses).deleteMemberCourses(7L);
    }

    @Test
    void withdrawLockedMemberRejectsAlreadyWithdrawn() {
        member.withdraw();
        assertThatThrownBy(() -> service.withdrawLockedMember(member)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(courses);
    }
}
