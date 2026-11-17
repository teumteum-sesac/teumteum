import { apiGet, apiPost } from '../../../shared/api/request'
import type { EmailExistsResponse, LoginRequest, LoginResponse, SignupRequest } from '../types'

/*
 * [이 파일이 하는 일]
 * 로그인·회원가입 API 호출 함수. 화면(LoginForm·SignupForm)은 이 함수만 부른다.
 * 성공하면 응답의 data 가 바로 나오고, 실패하면 ApiError 가 던져진다 (shared/api/request.ts).
 *
 * 로그아웃 API 는 없다: JWT 는 서버가 로그인 상태를 저장하지 않으므로 프론트가 토큰을 지우면 로그아웃이다 (authStore.logout).
 */

/** 로그인 (AUT-01). 실패: 401 LOGIN_FAILED "이메일 또는 비밀번호가 맞지 않아요." */
export function login(body: LoginRequest) {
  return apiPost<LoginResponse>('/auth/login', body)
}

/** 회원가입 (AUT-02). 성공하면 토큰까지 받아 바로 로그인. 실패: 409 DUPLICATE_EMAIL, 400 INVALID_REQUEST(fieldErrors) */
export function signup(body: SignupRequest) {
  return apiPost<LoginResponse>('/members', body)
}

/** 이메일 중복 확인 (AUT-02 [중복 확인]). exists=true 면 이미 가입된 이메일 */
export function checkEmailExists(email: string) {
  return apiGet<EmailExistsResponse>('/members/email-exists', { email })
}
