package com.teumteum.member.dto;

import com.teumteum.member.domain.Member;

/*
 * [이 파일이 하는 일]
 * 회원가입 성공 시 프론트에 돌려주는 데이터 = 응답 DTO. (AGENTS.md 5장: 응답은 XxxResponse)
 *   { "success": true,
 *     "data": { "memberId": 1, "email": "user@email.com", "nickname": "틈틈이" },
 *     "error": null }
 *   ↑ 바깥의 success/data/error 는 ApiResponse 가 감싸고, data 안이 이 클래스다.
 *
 * [엔티티(Member)를 그대로 응답하지 않는 이유]
 *   Member 를 그대로 내보내면 passwordHash 까지 JSON 에 실려 나간다 (NFR-005 민감 정보 노출).
 *   응답 DTO 에 "보여 줄 값"만 골라 담는다.
 */
public record SignupResponse(
        Long memberId,
        String email,
        String nickname
) {

    /**
     * Member 엔티티 → 응답 DTO 변환.
     * 변환 코드를 여기 한 곳에 두면, 응답 항목이 바뀔 때 이 메서드만 고치면 된다.
     */
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
