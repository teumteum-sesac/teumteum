import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuthStore } from '../../../shared/auth/authStore'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import Icon, { type IconName } from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import { useSavedCourseCount } from '../hooks/useSavedCourse'
import { useMemberInfo } from '../hooks/useMemberInfo'

/**
 * 마이페이지 (MY-01)
 * - 회원: 닉네임·이메일, 메뉴(저장한 코스 / 회원 정보 수정 / 로그아웃 / 회원 탈퇴)
 * - 비회원: 로그인 안내와 로그인 버튼
 * 디자인 (목업 마이페이지): 토끼 프로필 카드 + 저장 수 → 메뉴 종이 카드(모서리에서 거북이 빼꼼)
 */
export default function MyPageContent() {
  const navigate = useNavigate()
  const { isLoggedIn, user, logout } = useAuthStore()
  // 저장 건수: GET /api/courses?page=0&size=1 의 totalElements. 비회원이면 회원 전용 API 를 부르지 않는다
  const { data, loading, error, retry } = useSavedCourseCount(isLoggedIn && user !== null)
  const savedCount = data?.totalElements
  const member = useMemberInfo(isLoggedIn)
  const [confirmLogout, setConfirmLogout] = useState(false)

  if (!isLoggedIn || !user) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 pb-10 text-center">
        <StickerImage name="sceneRelax" className="w-64" ratio="aspect-[3/2]" />
        <p className="font-display text-xl">로그인하고 코스를 모아 보세요</p>
        <p className="text-sm text-neutral-500">마음에 드는 코스를 저장해 두고 언제든 다시 꺼내 볼 수 있어요</p>
        <Button className="mt-2 w-auto px-10" onClick={() => navigate('/login', { state: { from: '/mypage' } })}>
          로그인
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      {/* 프로필 카드: 토끼 얼굴(핑크 원) + 닉네임·이메일, 아래 저장한 코스 수 */}
      <section className="paper-card p-4">
        <div className="flex items-center gap-3">
          <span className="flex size-16 shrink-0 items-center justify-center rounded-full border-2 border-ink bg-primary-light">
            <StickerImage name="rabbitHead" className="size-14" />
          </span>
          <div className="min-w-0">
            <p className="truncate font-display text-2xl">{member.data?.nickname ?? user.nickname}</p>
            <p className="truncate text-sm text-neutral-500">{member.data?.email ?? user.email}</p>
          </div>
        </div>
        {member.error && <p role="alert" className="mt-2 text-xs text-red-600">{member.error} <button type="button" onClick={member.retry} className="underline">회원 정보 다시 조회</button></p>}
        <Link
          to="/mypage/courses"
          className="mt-4 flex items-center justify-between rounded-2xl border-2 border-dashed border-line px-4 py-2.5"
        >
          <span className="flex items-center gap-2 text-sm font-bold">
            <StickerImage name="navSaved" className="size-7" />
            저장한 코스
          </span>
          <span className="font-display text-2xl text-primary-dark">{loading ? '…' : (savedCount ?? '-')}</span>
        </Link>
        {error && (
          <p role="alert" className="mt-2 flex items-center justify-between gap-2 px-1 text-xs text-red-600">
            {error}
            <button type="button" onClick={retry} className="shrink-0 font-bold underline underline-offset-2">
              다시 조회
            </button>
          </p>
        )}
      </section>

      {/* 메뉴 카드. 오른쪽 아래 모서리에서 거북이가 고개를 내민다 (카드 위로 겹쳐 그림) */}
      <section className="relative">
        <ul className="paper-card overflow-hidden">
          <MenuItem to="/mypage/courses" icon="bookmark" label="저장한 코스" />
          <MenuItem to="/mypage/edit" icon="user" label="회원 정보 수정" />
          {/* 관리자 계정으로 로그인했을 때만 (REQ-044) */}
          {user.admin && <MenuItem to="/admin" icon="settings" label="관리자 페이지" />}
          <li className="border-b-2 border-dashed border-line last:border-0">
            <button type="button" onClick={() => setConfirmLogout(true)} className="flex h-14 w-full items-center gap-3 px-4 text-left text-sm font-bold">
              <Icon name="chevronLeft" className="size-5 text-neutral-500" />
              로그아웃
            </button>
          </li>
        </ul>
        <StickerImage
          name="turtlePeek"
          className="pointer-events-none absolute -right-1 -bottom-3 w-28"
          ratio="aspect-[3/2]"
        />
      </section>

      <Link to="/mypage/edit?withdraw=1" className="self-start px-1 text-xs text-neutral-400 underline underline-offset-4">
        회원 탈퇴
      </Link>

      {confirmLogout && (
        <ConfirmDialog
          message="로그아웃할까요?"
          confirmLabel="로그아웃"
          onCancel={() => setConfirmLogout(false)}
          onConfirm={() => {
            logout()
            navigate('/', { replace: true })
          }}
        />
      )}
    </div>
  )
}

function MenuItem({ to, icon, label }: { to: string; icon: IconName; label: string }) {
  return (
    <li className="border-b-2 border-dashed border-line last:border-0">
      <Link to={to} className="flex h-14 items-center gap-3 px-4 text-sm font-bold">
        <Icon name={icon} className="size-5 text-primary-dark" />
        <span className="flex-1">{label}</span>
        <Icon name="chevronRight" className="size-4 text-neutral-400" />
      </Link>
    </li>
  )
}
