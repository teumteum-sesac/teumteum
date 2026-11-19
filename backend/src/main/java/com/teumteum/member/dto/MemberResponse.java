package com.teumteum.member.dto;

// 본인 정보 조회 응답. 비밀번호와 비밀번호 해시는 포함하지 않는다.
public record MemberResponse(
        String email,
        String nickname
) {
}
