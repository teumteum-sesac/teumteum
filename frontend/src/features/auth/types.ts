/*
 * [이 파일이 하는 일]
 * 로그인·회원가입 API 의 요청·응답 모양. 이름과 필드는 백엔드 DTO 와 똑같이 맞춘다 (AGENTS.md 6장).
 *   backend/src/main/java/com/teumteum/member/dto/
 */

/** POST /api/auth/login 요청 (LoginRequest.java) */
export interface LoginRequest {
  email: string
  password: string
}

/** POST /api/members 요청 (SignupRequest.java). 비밀번호 8~64자, 닉네임 최대 50자 */
export interface SignupRequest {
  email: string
  password: string
  nickname: string
}

/**
 * 로그인·회원가입 공통 응답 (LoginResponse.java). 가입하면 바로 로그인 상태가 되도록 토큰을 같이 준다.
 * 프론트는 accessToken 을 저장해 두고 매 요청 Authorization: Bearer {토큰} 으로 보낸다 (shared/api/client.ts)
 */
export interface LoginResponse {
  accessToken: string
  /** 항상 "Bearer" */
  tokenType: string
  /** 토큰 유효 시간(초). 지나면 서버가 401 → client.ts 가 로그아웃 처리 */
  expiresIn: number
  memberId: number
  email: string
  nickname: string
  /** 관리자면 true (admin 테이블에 있는 회원). 마이페이지 [관리자] 메뉴 표시용 */
  admin: boolean
}

/** GET /api/members/email-exists?email= 응답 (EmailExistsResponse.java) */
export interface EmailExistsResponse {
  exists: boolean
}
