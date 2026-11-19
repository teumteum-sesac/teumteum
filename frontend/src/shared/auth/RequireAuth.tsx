import { Outlet, useLocation, useNavigate } from 'react-router'
import { useGoBack } from '../hooks/useGoBack'
import Button from '../components/Button'
import EmptyState from '../components/EmptyState'
import { useAuthStore } from './authStore'
import { LOGIN_REQUIRED_DESCRIPTION, LOGIN_REQUIRED_MESSAGE } from './LoginRequiredDialog'

/** 로그인 후 돌아갈 곳. 로그인 화면이 location.state 로 받는다 */
export interface LoginRedirectState {
  from: string
  /** 'save': 코스 저장 중이었음 → 로그인 후 저장을 이어서 진행 */
  pendingAction?: 'save'
}

/**
 * 회원 전용 화면을 감싼다 (router.tsx).
 * 비로그인이면 로그인 화면으로 바로 보내지 않고, 먼저 "로그인이 필요한 서비스입니다" 안내를 보여 준다.
 *   [로그인] → 로그인 화면 → 로그인 후 원래 화면으로 복귀 / [돌아가기] → 이전 화면(없으면 홈)
 * 하단바처럼 누르기 전에 막을 수 있는 곳은 확인 창(LoginRequiredDialog)을 띄우고, 이 안내는
 * 바로가기·주소 직접 입력·로그아웃 직후처럼 화면에 들어와 버린 경우를 맡는다.
 */
export default function RequireAuth() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const location = useLocation()
  const navigate = useNavigate()
  const goBack = useGoBack('/')

  if (isLoggedIn) return <Outlet />

  // 로그인 화면으로 갈 때 이 안내 화면은 기록에서 지운다(replace). 로그인 후 같은 주소로 돌아오므로,
  // 남겨 두면 같은 화면이 기록에 두 번 쌓여 뒤로가기를 한 번 더 눌러야 한다

  const state: LoginRedirectState = { from: location.pathname + location.search }
  // 이 탭에서 처음 연 화면이면 뒤로 갈 곳이 없다 (react-router 첫 화면의 key 는 'default')
  const canGoBack = location.key !== 'default'

  return (
    <div className="flex flex-1 flex-col justify-center">
      <EmptyState
        sticker="sceneRelax"
        message={LOGIN_REQUIRED_MESSAGE}
        description={`${LOGIN_REQUIRED_DESCRIPTION} 저장한 코스와 회원 정보는 로그인 후 볼 수 있어요.`}
        action={
          <>
            <Button variant="pink" className="mx-auto w-auto px-10" onClick={() => navigate('/login', { state, replace: true })}>
              로그인
            </Button>
            <button
              type="button"
              onClick={goBack}
              className="mx-auto mt-1 text-sm text-neutral-500 underline underline-offset-4"
            >
              {canGoBack ? '돌아가기' : '홈으로'}
            </button>
          </>
        }
      />
    </div>
  )
}
