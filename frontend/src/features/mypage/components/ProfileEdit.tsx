import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../shared/api/apiError'
import { useNavigate, useSearchParams } from 'react-router'
import { useGoBack } from '../../../shared/hooks/useGoBack'
import { useAuthStore } from '../../../shared/auth/authStore'
import Button from '../../../shared/components/Button'
import StickerImage from '../../../shared/components/StickerImage'
import TextField from '../../../shared/components/TextField'
import EmptyState from '../../../shared/components/EmptyState'
import LoadingState from '../../../shared/components/LoadingState'
import { updateMyInfo, withdrawMyAccount } from '../api/memberApi'
import { useMemberInfo } from '../hooks/useMemberInfo'
import type { MemberResponse } from '../types'

/**
 * 회원 정보 수정·탈퇴 (MY-04)
 * - 이메일(수정 불가), 닉네임, 비밀번호 변경(새 비밀번호 + 확인). 저장하면 마이페이지로
 * - 회원 탈퇴: 안내(저장한 코스 즉시 삭제) → 의사 확인(동의 체크 + 현재 비밀번호 둘 다) → 홈으로
 *   (체크만으로 탈퇴되면 토큰이 남은 기기에서 다른 사람이 계정을 지울 수 있어 비밀번호도 받는다)
 * - MY-01 '회원 탈퇴' 메뉴로 들어오면(?withdraw=1) 탈퇴 영역을 펼친 채로 시작
 * - 서버 조회로 입력값을 채우고 수정·탈퇴 성공 응답 뒤에만 로그인 상태를 변경한다
 */
export default function ProfileEdit() {
  const info = useMemberInfo()
  if (info.loading) return <LoadingState message="회원 정보를 불러오는 중이에요" />
  if (!info.data) return <EmptyState message="회원 정보를 불러오지 못했어요" description={info.error ?? undefined} action={<Button onClick={info.retry}>다시 시도</Button>} />
  return <ProfileEditForm key={info.data.email} member={info.data} />
}

function ProfileEditForm({ member }: { member: MemberResponse }) {
  const navigate = useNavigate()
  // 수정 완료 → 들어온 마이페이지로 돌아간다 (마이페이지를 새로 열면 같은 화면이 기록에 두 번 남는다)
  const backToMyPage = useGoBack('/mypage')
  const [searchParams] = useSearchParams()
  const { updateUser, logout } = useAuthStore()
  const [nickname, setNickname] = useState(member.nickname)
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [withdrawOpen, setWithdrawOpen] = useState(searchParams.get('withdraw') === '1')
  const [withdrawPassword, setWithdrawPassword] = useState('')
  const [withdrawAgreed, setWithdrawAgreed] = useState(false)
  const [busy, setBusy] = useState<'save' | 'withdraw' | null>(null)
  const [saveError, setSaveError] = useState<ApiError | null>(null)
  const [withdrawError, setWithdrawError] = useState<string | null>(null)
  const inFlight = useRef(false)
  const active = useRef(true)
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])

  const nicknameError = nickname.length > 50 ? '닉네임은 50자 이하로 입력해 주세요.' : undefined
  const passwordError = newPassword && (newPassword.trim().length === 0 || newPassword.length < 8 || newPassword.length > 64 || new TextEncoder().encode(newPassword).length > 72)
    ? '비밀번호는 8~64자, 최대 72바이트로 입력해 주세요.' : undefined
  const confirmError =
    newPasswordConfirm && newPassword !== newPasswordConfirm ? '비밀번호가 일치하지 않아요.' : undefined
  const canSave =
    nickname.trim().length > 0 && !nicknameError && !passwordError && newPassword === newPasswordConfirm

  const handleSave = async () => {
    if (!canSave || inFlight.current) return
    inFlight.current = true
    setBusy('save')
    setSaveError(null)
    const token = useAuthStore.getState().accessToken
    try {
      const result = await updateMyInfo({ nickname, ...(newPassword ? { newPassword, newPasswordConfirm } : {}) })
      if (!active.current || useAuthStore.getState().accessToken !== token) return
      updateUser(result)
      backToMyPage()
    } catch (e) {
      if (active.current) setSaveError(e instanceof ApiError ? e : new ApiError('UNKNOWN', '회원 정보를 수정하지 못했어요.', 0))
    } finally {
      inFlight.current = false
      if (active.current) setBusy(null)
    }
  }

  // 동의 체크와 비밀번호(백엔드와 같은 1~64자, 공백만은 불가)가 모두 있어야 탈퇴 버튼이 켜진다
  const canWithdraw = withdrawAgreed && withdrawPassword.trim().length > 0 && withdrawPassword.length <= 64

  const handleWithdraw = async () => {
    if (inFlight.current || !canWithdraw) return
    inFlight.current = true
    setBusy('withdraw')
    setWithdrawError(null)
    const token = useAuthStore.getState().accessToken
    try {
      const result = await withdrawMyAccount({ password: withdrawPassword, agreed: withdrawAgreed })
      if (!active.current || useAuthStore.getState().accessToken !== token) return
      if (!result.withdrawn) throw new Error('Unexpected withdrawal response')
      logout()
      navigate('/', { replace: true })
    } catch (e) {
      if (active.current) setWithdrawError(e instanceof ApiError ? e.message : '탈퇴하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      inFlight.current = false
      if (active.current) setBusy(null)
    }
  }

  return (
    <fieldset disabled={busy !== null} aria-busy={busy !== null} className="flex flex-col gap-4 px-4 py-5">
      <div className="flex justify-center pb-1">
        <span className="flex size-20 items-center justify-center rounded-full border-2 border-ink bg-primary-light">
          <StickerImage name="rabbitHead" className="size-[72px]" />
        </span>
      </div>
      <TextField label="이메일" name="email" value={member.email} disabled hint="이메일은 수정할 수 없어요" />
      <TextField label="닉네임" name="nickname" value={nickname} maxLength={50} onChange={(e) => setNickname(e.target.value)} error={nicknameError ?? saveError?.getFieldMessage('nickname')} />
      <TextField
        label="새 비밀번호"
        name="newPassword"
        type="password"
        autoComplete="new-password"
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
        error={passwordError ?? saveError?.getFieldMessage('newPassword') ?? saveError?.getFieldMessage('passwordChangeValid')}
        hint="바꾸지 않으려면 비워두세요"
      />
      <TextField
        label="새 비밀번호 확인"
        name="newPasswordConfirm"
        type="password"
        autoComplete="new-password"
        value={newPasswordConfirm}
        onChange={(e) => setNewPasswordConfirm(e.target.value)}
        error={confirmError ?? saveError?.getFieldMessage('newPasswordConfirm')}
      />
      {saveError && <p role="alert" className="text-sm text-red-600">{saveError.message}</p>}
      <Button onClick={() => { void handleSave() }} disabled={!canSave || busy !== null}>
        {busy === 'save' ? '저장 중…' : '저장'}
      </Button>

      <section className="mt-6 border-t-2 border-dashed border-line pt-5">
        {!withdrawOpen ? (
          <button type="button" onClick={() => setWithdrawOpen(true)} className="px-1 text-sm text-neutral-400 underline underline-offset-4">
            회원 탈퇴
          </button>
        ) : (
          <div className="flex flex-col gap-3 rounded-2xl border-2 border-dashed border-red-300 bg-red-50/60 p-4 text-sm">
            <p className="font-display text-lg">정말 탈퇴할까요?</p>
            <p className="text-neutral-600">탈퇴하면 저장한 코스가 즉시 삭제되고 되돌릴 수 없어요.</p>
            <TextField
              label="비밀번호 확인"
              name="withdrawPassword"
              type="password"
              autoComplete="current-password"
              value={withdrawPassword}
              onChange={(e) => setWithdrawPassword(e.target.value)}
              hint="현재 비밀번호를 입력하고 아래에 체크해 주세요"
            />
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={withdrawAgreed} onChange={(e) => setWithdrawAgreed(e.target.checked)} className="size-4 accent-primary" />
              안내를 확인했고 탈퇴에 동의합니다
            </label>
            {withdrawError && <p role="alert" className="text-sm text-red-600">{withdrawError}</p>}
            <Button variant="secondary" disabled={busy !== null || !canWithdraw} onClick={() => { void handleWithdraw() }}>
              {busy === 'withdraw' ? '탈퇴 중…' : '탈퇴하기'}
            </Button>
          </div>
        )}
      </section>
    </fieldset>
  )
}
