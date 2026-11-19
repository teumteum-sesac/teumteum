package com.teumteum.member.service;

import com.teumteum.admin.service.AdminService;
import com.teumteum.course.service.CourseService;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.member.domain.Member;
import com.teumteum.member.dto.MemberWithdrawRequest;
import com.teumteum.member.dto.MemberWithdrawResponse;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.repository.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/*
 * [이 파일이 하는 일]
 * 회원 탈퇴의 유일한 처리 경로. 본인 탈퇴(MY-04)와 관리자 강제 탈퇴(REQ-045)가 모두 여기를 거친다.
 *   회원 행 잠금 → 저장 코스 삭제 → 상태 WITHDRAWN + 이메일 비식별화
 * 경로마다 따로 구현하면 한쪽에서 코스 삭제가 빠지는 일이 생기므로(이전 관리자 강제 탈퇴 버그) 한 곳에 모은다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {
    private final MemberRepository memberRepository;
    private final CourseService courseService;
    private final PasswordEncoder passwordEncoder;
    private final AdminService adminService;

    // 코스 삭제와 회원 상태 변경을 하나로 묶는다. 어느 단계든 실패하면 모두 롤백한다.
    @Transactional
    public MemberWithdrawResponse withdrawMyAccount(Long memberId, MemberWithdrawRequest request) {
        if (memberId == null || memberId <= 0) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        // 토큰만으로 계정을 지울 수 없도록 동의 체크와 현재 비밀번호를 모두 요구한다.
        if (request == null || !request.isConfirmed() || request.password().length() > 64) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        Member member = lockMember(memberId).filter(Member::isActive)
                .orElseThrow(() -> new CustomException(CommonErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new CustomException(MemberErrorCode.WITHDRAWAL_PASSWORD_MISMATCH);
        }
        // 마지막 관리자가 스스로 탈퇴하면 관리자 화면에 아무도 들어갈 수 없다. 관리자 해제는 DB 에서 먼저 한다.
        if (adminService.isAdmin(memberId)) {
            throw new CustomException(MemberErrorCode.ADMIN_WITHDRAWAL_NOT_ALLOWED);
        }

        withdrawLockedMember(member);
        return new MemberWithdrawResponse(true);
    }

    /** 탈퇴 처리 전에 회원 행을 잠근다. 코스 저장(CourseSaveWriter)도 같은 행을 잠그므로 탈퇴 직후 코스가 새로 남지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Member> lockMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId);
    }

    /** lockMember 로 잠근 정상 회원을 탈퇴시킨다. 관리자 강제 탈퇴도 이 메서드를 쓴다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Member withdrawLockedMember(Member member) {
        if (!member.isActive()) {
            throw new IllegalStateException("이미 탈퇴한 회원입니다.");
        }
        // 다른 도메인의 Repository를 직접 사용하지 않고 코스 Service를 통해 삭제한다.
        courseService.deleteMemberCourses(member.getId());
        member.withdraw(); // 행을 삭제하지 않고 WITHDRAWN으로 바꾼다. JPA 변경 감지로 반영된다.
        return member;
    }
}
