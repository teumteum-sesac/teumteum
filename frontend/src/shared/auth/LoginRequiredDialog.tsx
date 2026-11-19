import { useNavigate } from 'react-router'
import ConfirmDialog from '../components/ConfirmDialog'
import type { LoginRedirectState } from './RequireAuth'

/*
 * [이 파일이 하는 일]
 * 비회원이 회원 전용 메뉴(저장한 코스 등)를 눌렀을 때, 로그인 화면으로 바로 보내지 않고 먼저 물어보는 확인 창.
 *   [로그인] → 로그인 화면 (로그인 후 from 화면으로 돌아온다) / [취소] → 지금 화면 그대로
 *
 *   {askLogin && <LoginRequiredDialog from="/mypage/courses" onClose={() => setAskLogin(false)} />}
 */
export const LOGIN_REQUIRED_MESSAGE = '로그인이 필요한 서비스입니다.'
export const LOGIN_REQUIRED_DESCRIPTION = '로그인하시겠습니까?'

export default function LoginRequiredDialog({ from, onClose }: { from: string; onClose: () => void }) {
  const navigate = useNavigate()

  return (
    <ConfirmDialog
      message={LOGIN_REQUIRED_MESSAGE}
      description={LOGIN_REQUIRED_DESCRIPTION}
      confirmLabel="로그인"
      onCancel={onClose}
      onConfirm={() => {
        onClose()
        const state: LoginRedirectState = { from }
        navigate('/login', { state })
      }}
    />
  )
}
