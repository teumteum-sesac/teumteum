package com.teumteum.admin.dto;

import com.teumteum.member.domain.AccountStatus;
import com.teumteum.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 관리자 회원 목록의 회원 1명 (REQ-045). 비밀번호 해시는 절대 내보내지 않는다 (NFR-005) */
@Schema(description = "관리자 화면의 회원 1명")
public record AdminMemberResponse(
        @Schema(description = "회원 번호", example = "1") Long memberId,
        @Schema(description = "이메일", example = "user@email.com") String email,
        @Schema(description = "닉네임", example = "틈틈이") String nickname,
        @Schema(description = "상태 ACTIVE 정상 / WITHDRAWN 탈퇴", example = "ACTIVE") AccountStatus accountStatus,
        @Schema(description = "관리자 여부") boolean admin,
        @Schema(description = "가입 시각") LocalDateTime createdAt
) {

    public static AdminMemberResponse of(Member member, boolean admin) {
        return new AdminMemberResponse(member.getId(), member.getEmail(), member.getNickname(),
                member.getAccountStatus(), admin, member.getCreatedAt());
    }
}
