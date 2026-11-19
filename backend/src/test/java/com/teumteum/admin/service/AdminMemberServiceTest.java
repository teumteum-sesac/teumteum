package com.teumteum.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.teumteum.admin.dto.AdminMemberResponse;
import com.teumteum.admin.dto.PageResponse;
import com.teumteum.admin.exception.AdminErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.member.domain.AccountStatus;
import com.teumteum.member.domain.Member;
import com.teumteum.member.service.MemberService;
import com.teumteum.member.service.MemberWithdrawalService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/*
 * [이 파일이 하는 일]
 * 관리자 회원 관리(REQ-045) 규칙 확인: 목록에 관리자 표시, 페이지 크기 제한, 강제 탈퇴 금지 조건.
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.admin.*"
 */
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private AdminService adminService;

    @Mock
    private MemberWithdrawalService memberWithdrawalService;

    private AdminMemberService adminMemberService;

    @BeforeEach
    void setUp() {
        adminMemberService = new AdminMemberService(memberService, adminService, memberWithdrawalService);
    }

    private Member member(Long id, String email) {
        Member member = Member.create(email, "hash", "닉네임" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("회원 목록: 관리자인 회원은 admin=true 로 표시된다")
    void listMarksAdmins() {
        given(memberService.searchMembers(eq("test"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(member(1L, "admin@test.com"), member(2L, "user@test.com"))));
        given(adminService.findAdminIds(anyCollection())).willReturn(Set.of(1L));

        PageResponse<AdminMemberResponse> result = adminMemberService.getMembers("test", 0, 20);

        assertThat(result.content()).extracting(AdminMemberResponse::memberId, AdminMemberResponse::admin)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, true), org.assertj.core.groups.Tuple.tuple(2L, false));
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("회원 목록: 한 페이지 개수는 최대 50, 음수 페이지는 0으로")
    void clampsPaging() {
        given(memberService.searchMembers(any(), any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        adminMemberService.getMembers(null, -3, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberService).searchMembers(eq(null), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(AdminMemberService.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("강제 탈퇴: 본인 탈퇴와 같은 경로(잠금 → 코스 삭제 → 상태 변경)를 거친다")
    void withdraw() {
        Member target = member(2L, "user@test.com");
        given(memberWithdrawalService.lockMember(2L)).willReturn(Optional.of(target));
        given(memberWithdrawalService.withdrawLockedMember(target)).willAnswer(invocation -> {
            target.withdraw();
            return target;
        });

        AdminMemberResponse result = adminMemberService.withdraw(1L, 2L);

        assertThat(result.accountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        verify(memberWithdrawalService).withdrawLockedMember(target);
    }

    @Test
    @DisplayName("강제 탈퇴: 본인은 불가 (CANNOT_WITHDRAW_SELF)")
    void cannotWithdrawSelf() {
        assertThatThrownBy(() -> adminMemberService.withdraw(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.CANNOT_WITHDRAW_SELF);
        verify(memberWithdrawalService, never()).withdrawLockedMember(any());
    }

    @Test
    @DisplayName("강제 탈퇴: 다른 관리자는 불가, 없는 회원은 404, 이미 탈퇴면 409")
    void withdrawRules() {
        given(memberWithdrawalService.lockMember(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> adminMemberService.withdraw(1L, 9L))
                .extracting("errorCode").isEqualTo(AdminErrorCode.MEMBER_NOT_FOUND);

        given(adminService.isAdmin(3L)).willReturn(true);
        assertThatThrownBy(() -> adminMemberService.withdraw(1L, 3L))
                .extracting("errorCode").isEqualTo(AdminErrorCode.CANNOT_WITHDRAW_ADMIN);

        Member withdrawn = member(4L, "gone@test.com");
        withdrawn.withdraw();
        given(memberWithdrawalService.lockMember(4L)).willReturn(Optional.of(withdrawn));
        assertThatThrownBy(() -> adminMemberService.withdraw(1L, 4L))
                .extracting("errorCode").isEqualTo(AdminErrorCode.ALREADY_WITHDRAWN);

        verify(memberWithdrawalService, never()).withdrawLockedMember(any());
    }
}
