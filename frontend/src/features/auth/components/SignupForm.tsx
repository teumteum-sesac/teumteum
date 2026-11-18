import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import { useAuthStore } from '../../../shared/auth/authStore'
import type { LoginRedirectState } from '../../../shared/auth/RequireAuth'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import StickerImage from '../../../shared/components/StickerImage'
import TextField from '../../../shared/components/TextField'
import { checkEmailExists, signup } from '../api/authApi'

const TERMS = [
  { key: 'service', label: '[필수] 이용약관 동의' },
  { key: 'privacy', label: '[필수] 개인정보 수집·이용 동의' },
] as const
type TermKey = (typeof TERMS)[number]['key']

/**
 * 회원가입 (AUT-02): 이메일(중복 확인)·비밀번호(8자 이상)·확인·닉네임, 약관 동의
 * - [중복 확인] GET /api/members/email-exists → 사용 가능하면 통과, 이미 있으면 이메일 칸에 안내
 * - [가입하기] POST /api/members → 응답 토큰으로 바로 로그인(자동 로그인) → 원래 흐름 복귀
 * - 서버 검증 실패(400)는 입력칸별 문구, 중복(409)은 이메일 칸, 그 밖의 실패는 버튼 위 한 줄 안내
 */
export default function SignupForm() {
  const navigate = useNavigate()
  const location = useLocation()
  const redirect = location.state as LoginRedirectState | null
  const setLogin = useAuthStore((s) => s.setLogin)

  const [form, setForm] = useState({ email: '', password: '', passwordConfirm: '', nickname: '' })
  const [agreed, setAgreed] = useState<Record<TermKey, boolean>>({ service: false, privacy: false })

  const set = (key: keyof typeof form) => (value: string) => {
    setForm((f) => ({ ...f, [key]: value }))
    // 다시 고치면 그 칸의 서버 오류 문구는 지운다
    setServerErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  // 항목별 실시간 검증 (입력을 시작한 항목만 안내)
  const errors = {
    email: form.email && !/^\S+@\S+\.\S+$/.test(form.email) ? '이메일 형식을 확인해 주세요.' : undefined,
    password: form.password && form.password.length < 8 ? '비밀번호는 8자 이상이어야 해요.' : undefined,
    passwordConfirm: form.passwordConfirm && form.password !== form.passwordConfirm ? '비밀번호가 일치하지 않아요.' : undefined,
    nickname: undefined,
  }
  // 중복 확인을 통과한 이메일. 이메일을 바꾸면 다시 확인해야 한다
  const [checkedEmail, setCheckedEmail] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)
  // 서버가 알려준 오류: 입력칸별(serverErrors) / 폼 전체(formError)
  const [serverErrors, setServerErrors] = useState<Partial<Record<keyof typeof form, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const emailChecked = checkedEmail !== null && checkedEmail === form.email
  const filled = Object.values(form).every((v) => v.length > 0)
  const valid = filled && emailChecked && Object.values(errors).every((e) => !e)
  const allAgreed = TERMS.every((t) => agreed[t.key])
  // 화면에 보일 입력칸 오류: 입력 중 검증이 먼저, 없으면 서버가 준 문구
  const shownErrors = {
    email: errors.email ?? serverErrors.email,
    password: errors.password ?? serverErrors.password,
    passwordConfirm: errors.passwordConfirm,
    nickname: serverErrors.nickname,
  }

  const handleCheckEmail = async () => {
    setChecking(true)
    setServerErrors((prev) => ({ ...prev, email: undefined }))
    try {
      const { exists } = await checkEmailExists(form.email.trim())
      if (exists) {
        setCheckedEmail(null)
        setServerErrors((prev) => ({ ...prev, email: '이미 가입된 이메일이에요.' }))
      } else {
        setCheckedEmail(form.email)
      }
    } catch (err) {
      setServerErrors((prev) => ({ ...prev, email: err instanceof ApiError ? err.message : DEFAULT_ERROR_MESSAGE }))
    } finally {
      setChecking(false)
    }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!valid || !allAgreed || submitting) return
    setSubmitting(true)
    setFormError(null)
    try {
      const res = await signup({ email: form.email.trim(), password: form.password, nickname: form.nickname.trim() })
      // 가입 응답에 토큰이 있다 → 바로 로그인 상태 (REQ-001 가입 후 자동 로그인)
      setLogin(res.accessToken, { memberId: res.memberId, email: res.email, nickname: res.nickname, admin: res.admin })
    } catch (err) {
      setSubmitting(false)
      if (!(err instanceof ApiError)) {
        setFormError(DEFAULT_ERROR_MESSAGE)
        return
      }
      if (err.code === 'DUPLICATE_EMAIL') {
        // 확인 후 그 사이에 누가 가입한 경우: 다시 중복 확인하도록 되돌린다
        setCheckedEmail(null)
        setServerErrors({ email: err.message })
      } else if (err.fieldErrors.length > 0) {
        setServerErrors({
          email: err.getFieldMessage('email'),
          password: err.getFieldMessage('password'),
          nickname: err.getFieldMessage('nickname'),
        })
      } else {
        setFormError(err.message)
      }
      return
    }

    if (redirect?.pendingAction === 'save') {
      navigate(redirect.from, { replace: true, state: { afterLogin: 'save' } })
    } else {
      navigate(redirect?.from ?? '/', { replace: true })
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-1 flex-col" noValidate>
      <div className="flex flex-col gap-4 px-4 py-5">
        {/* 환영 인사: 토끼·거북이 얼굴 + 제목 */}
        <div className="flex items-center gap-1 pb-1">
          <StickerImage name="rabbitHead" className="size-11 -rotate-6" />
          <StickerImage name="turtleHead" className="size-11 rotate-6" />
          <p className="ml-1.5 font-display text-xl leading-tight">
            틈틈에 오신 걸
            <br />
            환영해요!
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <TextField
              label="이메일"
              name="email"
              type="email"
              value={form.email}
              onChange={(e) => set('email')(e.target.value)}
              error={shownErrors.email}
              hint={emailChecked ? '사용할 수 있는 이메일이에요' : '중복 확인을 해주세요'}
              autoComplete="email"
            />
          </div>
          <Button
            variant="secondary"
            className={`mb-5 w-auto shrink-0 px-4 ${emailChecked ? 'bg-turtle/50!' : ''}`}
            disabled={!form.email || Boolean(errors.email) || checking || emailChecked}
            onClick={() => void handleCheckEmail()}
          >
            {checking ? '확인 중…' : emailChecked ? '확인됨' : '중복 확인'}
          </Button>
        </div>
        <TextField label="비밀번호" name="password" type="password" autoComplete="new-password" maxLength={64} value={form.password} onChange={(e) => set('password')(e.target.value)} error={shownErrors.password} hint="8자 이상" />
        <TextField label="비밀번호 확인" name="passwordConfirm" type="password" autoComplete="new-password" maxLength={64} value={form.passwordConfirm} onChange={(e) => set('passwordConfirm')(e.target.value)} error={shownErrors.passwordConfirm} />
        <TextField
          label="닉네임"
          name="nickname"
          maxLength={50}
          value={form.nickname}
          onChange={(e) => set('nickname')(e.target.value)}
          error={shownErrors.nickname}
        />

        <fieldset className="paper-card mt-2 flex flex-col gap-2.5 p-4 text-sm">
          <label className="flex items-center gap-2 border-b-2 border-dashed border-line pb-2.5 font-bold">
            <input
              type="checkbox"
              checked={allAgreed}
              onChange={(e) => setAgreed({ service: e.target.checked, privacy: e.target.checked })}
              className="size-4 accent-primary"
            />
            전체 동의
          </label>
          {TERMS.map((t) => (
            <label key={t.key} className="flex items-center gap-2 text-neutral-600">
              <input
                type="checkbox"
                checked={agreed[t.key]}
                onChange={(e) => setAgreed((a) => ({ ...a, [t.key]: e.target.checked }))}
                className="size-4 accent-primary"
              />
              {t.label}
            </label>
          ))}
        </fieldset>
      </div>

      <BottomActionBar>
        <div className="flex w-full flex-col gap-2">
          {formError && (
            <p role="alert" className="rounded-xl border-2 border-dashed border-red-300 bg-red-50/70 px-3 py-2 text-center text-sm text-red-600">
              {formError}
            </p>
          )}
          <Button type="submit" variant="pink" disabled={!valid || !allAgreed || submitting}>
            {submitting ? '가입하는 중…' : '가입하기'}
          </Button>
        </div>
      </BottomActionBar>
    </form>
  )
}
