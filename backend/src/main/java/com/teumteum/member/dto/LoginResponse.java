package com.teumteum.member.dto;

import com.teumteum.member.domain.Member;

/*
 * [이 파일이 하는 일]
 * 로그인(또는 가입 직후 자동 로그인) 성공 시 프론트에 돌려주는 데이터.
 *   { "success": true,
 *     "data": {
 *       "accessToken": "eyJhbGciOi...", "tokenType": "Bearer", "expiresIn": 3600,
 *       "memberId": 1, "email": "user@email.com", "nickname": "틈틈이"
 *     },
 *     "error": null }
 *
 * [프론트에서 쓰는 법] (김용현, AUT-01 연결 때)
 *   authStore.setLogin(accessToken, { email, nickname })
 *   → shared/api/client.ts 가 이후 모든 요청에 "Authorization: Bearer {accessToken}" 을 자동으로 붙인다
 *
 * 회원 정보를 같이 주는 이유: 로그인 직후 마이페이지·하단바에 닉네임을 바로 보여 주려고 (요청을 한 번 더 안 해도 됨)
 */
public record LoginResponse(
        /** JWT 로그인 토큰 */
        String accessToken,
        /** 토큰 종류. 헤더에 "Bearer 토큰" 형태로 붙인다는 뜻 */
        String tokenType,
        /** 토큰 유효 시간(초) */
        long expiresIn,
        Long memberId,
        String email,
        String nickname
) {

    private static final String BEARER = "Bearer";

    public static LoginResponse of(Member member, String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, BEARER, expiresIn, member.getId(), member.getEmail(), member.getNickname());
    }
}
