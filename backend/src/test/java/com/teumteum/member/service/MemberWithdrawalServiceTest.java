package com.teumteum.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MemberWithdrawalServiceTest {
    private final MemberRepository repository = mock(MemberRepository.class);
    private final CourseService courses = mock(CourseService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final MemberWithdrawalService service = new MemberWithdrawalService(repository, courses, encoder);
    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.create("withdraw@example.com", encoder.encode("abc12345"), "탈퇴테스트");
    }

    @Test
    void withdrawsWithConsentAndDeletesOnlyAuthenticatedMembersCourses() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        assertThat(service.withdrawMyAccount(7L, new MemberWithdrawRequest(null, true)).withdrawn()).isTrue();
        assertThat(member.isActive()).isFalse();
        verify(courses).deleteMemberCourses(7L);
        verify(repository, never()).delete(any());
    }

    @Test
    void withdrawsWithCorrectPasswordWithoutCheckbox() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        service.withdrawMyAccount(7L, new MemberWithdrawRequest("abc12345", false));
        assertThat(member.isActive()).isFalse();
        verify(courses).deleteMemberCourses(7L);
    }

    @Test
    void rejectsWrongPasswordBeforeDeletingCourses() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest("wrong", false)))
                .isInstanceOfSatisfying(CustomException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.WITHDRAWAL_PASSWORD_MISMATCH));
        assertThat(member.isActive()).isTrue();
        verifyNoInteractions(courses);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsAbsentConfirmation(String password) {
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest(password, false)))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(repository, courses);
    }

    @Test
    void rejectsMissingOrAlreadyWithdrawnMember() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest(null, true)))
                .isInstanceOfSatisfying(CustomException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        member.withdraw();
        given(repository.findByIdForUpdate(8L)).willReturn(Optional.of(member));
        assertThatThrownBy(() -> service.withdrawMyAccount(8L, new MemberWithdrawRequest(null, true)))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(courses);
    }

    @Test
    void doesNotChangeStatusWhenCourseDeletionFails() {
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        doThrow(new IllegalStateException("test failure")).when(courses).deleteMemberCourses(7L);
        assertThatThrownBy(() -> service.withdrawMyAccount(7L, new MemberWithdrawRequest(null, true)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(member.isActive()).isTrue();
    }
}
