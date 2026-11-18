package com.teumteum.member.dto;

// 탈퇴 성공 여부만 반환한다. 프론트는 성공 후 토큰을 지우고 홈으로 이동한다.
public record MemberWithdrawResponse(boolean withdrawn) {
}
