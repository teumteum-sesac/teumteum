import axios, { type AxiosError } from 'axios'
import { useAuthStore } from '../auth/authStore'
import type { ApiResponse } from '../types/api'
import { ApiError, DEFAULT_ERROR_MESSAGE, NETWORK_ERROR } from './apiError'

/*
 * [이 파일이 하는 일]
 * 모든 API 호출이 거치는 axios 인스턴스. (토큰 첨부·에러 처리 일원화, AGENTS.md 6장)
 *   - 요청 보낼 때: 로그인 토큰이 있으면 Authorization 헤더에 붙인다
 *   - 실패 응답 받을 때: 어떤 오류든 ApiError 하나로 바꿔서 던진다
 *   - 401(로그인 필요·토큰 만료)이면 로그인 상태를 지운다
 *
 * 화면 코드는 이 client 를 직접 쓰지 말고 shared/api/request.ts 의 apiGet·apiPost 를 쓴다 (data 를 꺼내 준다).
 * /api 로 시작하는 요청은 개발 중 vite.config.ts 의 proxy 가 스프링 서버(8080)로 넘긴다.
 */
const client = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 요청: 로그인 토큰 첨부
client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 응답: 실패를 ApiError 로 통일
client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    // 1) 서버에 닿지 못함 (서버 꺼짐·인터넷 끊김·시간 초과)
    if (!error.response) {
      return Promise.reject(new ApiError(NETWORK_ERROR, '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.', 0))
    }

    const { status, data } = error.response

    // 2) 401: 토큰이 없거나 만료됨 → 로그인 상태 정리. 화면 이동은 RequireAuth·각 화면이 판단한다
    if (status === 401 && useAuthStore.getState().isLoggedIn) {
      useAuthStore.getState().logout()
    }

    // 3) 백엔드 공통 형식이면 그 code·message 를 그대로, 아니면 기본 문구
    const detail = data?.error
    return Promise.reject(
      new ApiError(detail?.code ?? `HTTP_${status}`, detail?.message ?? DEFAULT_ERROR_MESSAGE, status, detail?.fieldErrors),
    )
  },
)

export default client
