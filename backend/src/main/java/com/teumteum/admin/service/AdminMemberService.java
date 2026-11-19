package com.teumteum.admin.service;

import com.teumteum.admin.dto.AdminMemberResponse;
import com.teumteum.admin.dto.PageResponse;
import com.teumteum.admin.exception.AdminErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.member.domain.Member;
import com.teumteum.member.service.MemberService;
import com.teumteum.member.service.MemberWithdrawalService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * [이 파일이 하는 일]
 * 관리자의 회원 관리 (REQ-045): 회원 목록 검색, 강제 탈퇴.
 * 회원 데이터는 MemberService 를 통해서만 읽고 바꾼다 (다른 도메인 repository 직접 호출 금지, AGENTS.md 5장).
 *
 * [회원 상태 변경 범위]
 *   지금 상태 값은 ACTIVE(정상) / WITHDRAWN(탈퇴) 두 가지뿐이라 "강제 탈퇴"만 제공한다.
 *   정지(SUSPENDED) 같은 상태가 필요해지면 상태 값 목록을 김하윤과 확정한 뒤 추가한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    /** 한 페이지 최대 개수 (너무 큰 요청 방지) */
    static final int MAX_PAGE_SIZE = 50;

    private final MemberService memberService;
    private final AdminService adminService;
    private final MemberWithdrawalService memberWithdrawalService;

    public PageResponse<AdminMemberResponse> getMembers(String keyword, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // 최신 가입순 (같은 시각이면 번호 큰 순)
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Member> members = memberService.searchMembers(keyword, pageable);

        // 이번 페이지 회원 중 관리자만 한 번에 조회해서 표시
        Set<Long> adminIds = adminService.findAdminIds(members.map(Member::getId).getContent());
        return PageResponse.of(members, member -> AdminMemberResponse.of(member, adminIds.contains(member.getId())));
    }

    /**
     * 강제 탈퇴. 탈퇴하면 그 회원은 로그인할 수 없고, 남은 토큰도 JwtAuthenticationFilter 가 막는다.
     * 본인 탈퇴와 같은 경로(MemberWithdrawalService)를 거쳐 회원 행 잠금 → 저장 코스 삭제 → 상태 변경을 한 번에 한다.
     */
    @Transactional
    public AdminMemberResponse withdraw(Long adminMemberId, Long memberId) {
        if (memberId.equals(adminMemberId)) {
            throw new CustomException(AdminErrorCode.CANNOT_WITHDRAW_SELF);
        }
        if (adminService.isAdmin(memberId)) {
            throw new CustomException(AdminErrorCode.CANNOT_WITHDRAW_ADMIN);
        }
        // 잠근 뒤에 상태를 확인해야 본인 탈퇴·코스 저장과 동시에 일어나도 꼬이지 않는다.
        Member member = memberWithdrawalService.lockMember(memberId)
                .orElseThrow(() -> new CustomException(AdminErrorCode.MEMBER_NOT_FOUND));
        if (!member.isActive()) {
            throw new CustomException(AdminErrorCode.ALREADY_WITHDRAWN);
        }
        return AdminMemberResponse.of(memberWithdrawalService.withdrawLockedMember(member), false);
    }
}
