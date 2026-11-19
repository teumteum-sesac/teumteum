package com.teumteum.member.dto;

/*
 * [이 파일이 하는 일]
 * 이메일 중복 확인 결과를 돌려주는 응답 DTO.
 *   GET /api/members/email-exists?email=user@email.com
 *   → { "success": true, "data": { "exists": true }, "error": null }
 *
 * 회원가입 화면(AUT-02)의 [중복 확인] 버튼이 부른다.
 *   exists = true  → "이미 가입된 이메일이에요"
 *   exists = false → "사용할 수 있는 이메일이에요"
 *
 * boolean 하나지만 객체로 감싼 이유: 나중에 항목이 늘어도(예: 추천 문구) 응답 모양이 깨지지 않게.
 */
public record EmailExistsResponse(boolean exists) {
}
