import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

export interface AuthUser {
  /** 로그인 응답의 회원 ID (API 연동 전 임시 로그인 데이터에는 없을 수 있다) */
  memberId?: number
  email: string
  nickname: string
  /** 관리자면 true (로그인 응답의 admin). 관리자 화면(/admin) 진입 조건 */
  admin?: boolean
}

interface AuthState {
  accessToken: string | null
  user: AuthUser | null
  isLoggedIn: boolean
  setLogin: (accessToken: string, user: AuthUser) => void
  updateUser: (user: Partial<AuthUser>) => void
  logout: () => void
}

/**
 * 로그인 상태. 새로고침해도 유지되도록 localStorage 에 저장한다.
 * shared/api/client.ts 가 이 토큰을 요청 헤더에 붙이고, 서버가 401 을 주면(토큰 만료 등) logout 한다.
 * 로그아웃(REQ-003)은 서버 API 없이 토큰을 지우는 것으로 끝난다 (JWT 는 서버가 로그인 상태를 저장하지 않음).
 * 토큰 유효 시간은 1시간. 재발급(refresh)은 아직 없어서 만료되면 다시 로그인한다.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      isLoggedIn: false,
      setLogin: (accessToken, user) => set({ accessToken, user, isLoggedIn: true }),
      updateUser: (user) => set((state) => ({ user: state.user ? { ...state.user, ...user } : null })),
      logout: () => set({ accessToken: null, user: null, isLoggedIn: false }),
    }),
    { name: 'teumteum-auth', storage: createJSONStorage(() => localStorage) },
  ),
)
