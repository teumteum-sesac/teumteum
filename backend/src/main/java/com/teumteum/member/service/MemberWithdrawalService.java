package com.teumteum.member.service;

import com.teumteum.course.service.CourseService;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.member.domain.Member;
import com.teumteum.member.dto.MemberWithdrawRequest;
import com.teumteum.member.dto.MemberWithdrawResponse;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {
    private final MemberRepository memberRepository;
    private final CourseService courseService;
    private final PasswordEncoder passwordEncoder;

    // 코스 삭제와 회원 상태 변경을 하나로 묶는다. 어느 단계든 실패하면 모두 롤백한다.
    @Transactional
    public MemberWithdrawResponse withdrawMyAccount(Long memberId, MemberWithdrawRequest request) {
        if (memberId == null || memberId <= 0) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        if (request == null || !request.isConfirmed()
                || (request.password() != null && request.password().length() > 64)) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        Member member = memberRepository.findById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new CustomException(CommonErrorCode.UNAUTHORIZED));

        // 동의 체크만으로도 탈퇴 가능하다. 체크하지 않았으면 현재 비밀번호가 맞아야 한다.
        if (!request.agreed() && !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new CustomException(MemberErrorCode.WITHDRAWAL_PASSWORD_MISMATCH);
        }

        // 다른 도메인의 Repository를 직접 사용하지 않고 코스 Service를 통해 삭제한다.
        courseService.deleteMemberCourses(memberId);
        member.withdraw(); // 행을 삭제하지 않고 WITHDRAWN으로 바꾼다. JPA 변경 감지로 반영된다.
        return new MemberWithdrawResponse(true);
    }
}
