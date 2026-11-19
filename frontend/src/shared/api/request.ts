import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '../types/api'
import { ApiError, DEFAULT_ERROR_MESSAGE } from './apiError'
import client from './client'

/*
 * [이 파일이 하는 일]
 * API 를 부르고 공통 응답 { success, data, error } 에서 data 만 꺼내 돌려주는 함수 모음.
 * 각 feature 의 api 파일은 이 함수들만 쓰면 된다.
 *
 * [feature api 파일 작성 예] features/auth/api/authApi.ts
 *   import { apiGet, apiPost } from '../../../shared/api/request'
 *   import type { SignupRequest, SignupResponse, EmailExistsResponse } from '../types'
 *
 *   export function signup(body: SignupRequest) {
 *     return apiPost<SignupResponse>('/members', body)          // → Promise<SignupResponse>
 *   }
 *   export function checkEmailExists(email: string) {
 *     return apiGet<EmailExistsResponse>('/members/email-exists', { email })
 *   }
 *
 * [화면에서]
 *   const result = await signup(form)        // 성공: data 가 바로 나온다
 *   실패하면 ApiError 가 던져진다 → catch 에서 e.message 를 보여 준다 (shared/api/apiError.ts)
 *
 * 주소는 '/api' 를 빼고 쓴다 (client 의 baseURL 이 붙여 줌). 예: '/members' → /api/members
 */

/** 응답에서 data 꺼내기. success=false 면 ApiError 로 던진다 */
function unwrap<T>(body: ApiResponse<T>, status: number): T {
  if (body.success) return body.data as T
  throw new ApiError(body.error?.code ?? `HTTP_${status}`, body.error?.message ?? DEFAULT_ERROR_MESSAGE, status, body.error?.fieldErrors)
}

/** GET. params 는 주소 뒤 ?a=1&b=2 로 붙는다 */
export async function apiGet<T>(url: string, params?: AxiosRequestConfig['params']): Promise<T> {
  const res = await client.get<ApiResponse<T>>(url, { params })
  return unwrap(res.data, res.status)
}

/** POST. body 는 JSON 으로 보낸다 */
export async function apiPost<T>(url: string, body?: unknown): Promise<T> {
  const res = await client.post<ApiResponse<T>>(url, body)
  return unwrap(res.data, res.status)
}

/** PATCH (일부 수정). 예: 닉네임 변경 */
export async function apiPatch<T>(url: string, body?: unknown): Promise<T> {
  const res = await client.patch<ApiResponse<T>>(url, body)
  return unwrap(res.data, res.status)
}

/** DELETE. 탈퇴 확인 등 필요한 경우 body를 전달한다. 돌려줄 data가 없으면 apiDelete<void>(...) */
export async function apiDelete<T>(url: string, body?: unknown): Promise<T> {
  const res = await client.delete<ApiResponse<T>>(url, { data: body })
  return unwrap(res.data, res.status)
}
