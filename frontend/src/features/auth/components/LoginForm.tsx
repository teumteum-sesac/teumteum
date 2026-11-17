import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import { useAuthStore } from '../../../shared/auth/authStore'
import type { LoginRedirectState } from '../../../shared/auth/RequireAuth'
import Button from '../../../shared/components/Button'
import StickerImage from '../../../shared/components/StickerImage'
import TextField from '../../../shared/components/TextField'
import { login } from '../api/authApi'

/**
 * 로그인 (AUT-01): 이메일·비밀번호. 로그인 후 진입 전 화면으로 복귀
 * - 코스 저장 중이었으면 저장 완료 후 DET-01, 하단바 로그인이면 홈, 마이페이지였으면 MY-01
 * - POST /api/auth/login 성공 → 받은 토큰을 authStore 에 저장 (이후 모든 요청에 자동으로 붙음)
 * - 실패 → 서버가 준 문구를 폼 아래에 표시 (이메일·비밀번호 중 무엇이 틀렸는지는 알려주지 않는다: 보안)
 * TODO(김용현): 비밀번호 찾기 (REQ-054)
 */
export default function LoginForm() {
  const navigate = useNavigate()
  const location = useLocation()
  const redirect = location.state as LoginRedirectState | null
  const setLogin = useAuthStore((s) => s.setLogin)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<{ email?: string; password?: string }>({})
  // 서버 응답 오류 (로그인 실패·서버 꺼짐 등). 입력칸 아래가 아니라 버튼 위에 한 줄로 보여 준다
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (submitting) return
    const next: typeof error = {}
    if (!/^\S+@\S+\.\S+$/.test(email)) next.email = '이메일 형식을 확인해 주세요.'
    if (password.length === 0) next.password = '비밀번호를 입력해 주세요.'
    setError(next)
    setFormError(null)
    if (Object.keys(next).length > 0) return

    setSubmitting(true)
    try {
      const res = await login({ email: email.trim(), password })
      setLogin(res.accessToken, { memberId: res.memberId, email: res.email, nickname: res.nickname })
    } catch (err) {
      // 401 LOGIN_FAILED: "이메일 또는 비밀번호가 맞지 않아요." / 서버 꺼짐: NETWORK_ERROR 안내 문구
      setFormError(err instanceof ApiError ? err.message : DEFAULT_ERROR_MESSAGE)
      setSubmitting(false)
      return
    }

    if (redirect?.pendingAction === 'save') {
      navigate(redirect.from, { replace: true, state: { afterLogin: 'save' } })
    } else {
      navigate(redirect?.from ?? '/', { replace: true })
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-1 flex-col gap-4 px-6 pt-2 pb-8" noValidate>
      {/* 목업 스플래시 느낌: 로고 엠블럼 + 쉬는 듀오 */}
      <div className="mb-2 flex flex-col items-center text-center">
        <StickerImage name="brandLogo" alt="틈틈" className="w-44" ratio="aspect-[4/3]" />
        <StickerImage name="sceneRelax" className="-mt-2 w-56" ratio="aspect-[3/2]" />
        <p className="mt-1 text-sm text-neutral-500">
          {redirect?.pendingAction === 'save' ? '코스를 저장하려면 로그인이 필요해요' : '로그인하고 코스를 저장해 보세요'}
        </p>
      </div>

      <TextField label="이메일" name="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} error={error.email} />
      <TextField label="비밀번호" name="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} error={error.password} />

      {formError && (
        <p role="alert" className="rounded-xl border-2 border-dashed border-red-300 bg-red-50/70 px-3 py-2 text-center text-sm text-red-600">
          {formError}
        </p>
      )}

      <Button type="submit" className="mt-2" disabled={submitting}>
        {submitting ? '로그인 중…' : '로그인'}
      </Button>

      <div className="flex justify-center gap-4 text-sm font-bold text-neutral-500">
        <Link to="/signup" state={redirect}>
          회원가입
        </Link>
        <span className="text-line">|</span>
        <button type="button" onClick={() => alert('가입한 이메일로 재설정 링크를 보내는 기능은 준비 중입니다.')}>
          비밀번호 찾기
        </button>
      </div>

      <button type="button" onClick={() => navigate(-1)} className="mt-auto text-sm text-neutral-400 underline underline-offset-4">
        로그인 없이 돌아가기
      </button>
    </form>
  )
}
