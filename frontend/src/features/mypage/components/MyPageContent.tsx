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
 * 디자인 (목업 마이페이지): 핑크 띠 토끼 프로필 카드(테이프) + 저장 수 → '내 정보 관리' 메뉴 종이 카드(핑크 동그라미 아이콘, 모서리에서 거북이 빼꼼)
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
        <Button className="mt-2 w-auto px-10" onClick={() => navigate('/login', { replace: true, state: { from: '/mypage' } })}>
          로그인
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 p-4 pt-6">
      {/* 프로필 카드: 핑크 띠 위에 토끼 프로필 + 닉네임·이메일, 아래 저장한 코스 수 (모서리 테이프) */}
      <section className="paper-card relative overflow-visible">
        <StickerImage name="decoTapeYellow" className="pointer-events-none absolute -top-3.5 left-6 z-10 w-16 -rotate-6" ratio="aspect-[3/2]" />
        <div className="flex items-center gap-3 rounded-t-[14px] bg-primary-light/70 px-4 pt-6 pb-4">
          <span className="flex size-16 shrink-0 items-center justify-center rounded-full border-2 border-ink bg-cream shadow-[0_2px_0_var(--color-ink)]">
            <StickerImage name="rabbitHead" className="size-13" />
          </span>
          <div className="min-w-0">
            <p className="truncate font-display text-2xl leading-tight">
              {member.data?.nickname ?? user.nickname}
              <span className="ml-1 text-base text-neutral-500">님</span>
            </p>
            <p className="mt-0.5 truncate text-xs text-neutral-500">{member.data?.email ?? user.email}</p>
          </div>
        </div>
        <Link to="/mypage/courses" className="flex items-center gap-3 px-4 py-3.5 active:bg-primary-light/30">
          <StickerImage name="navSaved" className="size-9 -rotate-6" />
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-bold">저장한 코스</span>
            <span className="block text-[11px] text-neutral-500">모아 둔 코스 보러 가기</span>
          </span>
          <span className="font-display text-3xl text-primary-dark">{loading ? '…' : (savedCount ?? '-')}</span>
          <Icon name="chevronRight" className="size-5 text-neutral-400" />
        </Link>
      </section>
      {member.error && <p role="alert" className="-mt-3 px-1 text-xs text-red-600">{member.error} <button type="button" onClick={member.retry} className="underline">회원 정보 다시 조회</button></p>}
      {error && (
        <p role="alert" className="-mt-3 flex items-center justify-between gap-2 px-1 text-xs text-red-600">
          {error}
          <button type="button" onClick={retry} className="shrink-0 font-bold underline underline-offset-2">
            다시 조회
          </button>
        </p>
      )}

      {/* 메뉴 카드. 오른쪽 아래 모서리에서 거북이가 고개를 내민다 (카드 위로 겹쳐 그림) */}
      <section>
        <h3 className="mb-2.5 px-1 font-display text-lg">내 정보 관리</h3>
        <div className="relative">
          <ul className="paper-card overflow-hidden">
            {/* 저장한 코스는 위 프로필 카드에서 바로 간다 (메뉴에 중복으로 두지 않음) */}
            <MenuItem to="/mypage/edit" icon="user" label="회원 정보 수정" />
            {/* 관리자 계정으로 로그인했을 때만 (REQ-044) */}
            {user.admin && <MenuItem to="/admin" icon="settings" label="관리자 페이지" />}
            <li className="border-b-2 border-dashed border-line last:border-0">
              <button type="button" onClick={() => setConfirmLogout(true)} className="flex h-15 w-full items-center gap-3 px-4 text-left text-sm font-bold text-neutral-600 active:bg-primary-light/30">
                <MenuIcon icon="logout" muted />
                로그아웃
              </button>
            </li>
          </ul>
          <StickerImage
            name="turtlePeek"
            className="pointer-events-none absolute -right-1 -bottom-3 w-28"
            ratio="aspect-[3/2]"
          />
        </div>
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
      <Link to={to} className="flex h-15 items-center gap-3 px-4 text-sm font-bold active:bg-primary-light/30">
        <MenuIcon icon={icon} />
        <span className="flex-1">{label}</span>
        <Icon name="chevronRight" className="size-4 text-neutral-400" />
      </Link>
    </li>
  )
}

/** 메뉴 아이콘: 연한 핑크 동그라미 안에 아이콘 (로그아웃은 회색) */
function MenuIcon({ icon, muted = false }: { icon: IconName; muted?: boolean }) {
  return (
    <span className={`flex size-9 shrink-0 items-center justify-center rounded-full border-2 ${muted ? 'border-line bg-paper text-neutral-500' : 'border-primary/40 bg-primary-light text-primary-dark'}`}>
      <Icon name={icon} className="size-4.5" strokeWidth={2.4} />
    </span>
  )
}
