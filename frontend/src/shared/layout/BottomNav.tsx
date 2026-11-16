import { NavLink } from 'react-router'
import type { StickerKey } from '../assets/stickerAssets'
import { useAuthStore } from '../auth/authStore'
import StickerImage from '../components/StickerImage'

/**
 * 하단바: 홈·주변 탐색 화면에만 고정 (화면설계서 v2.0)
 * 비로그인 상태에서 '저장한 코스'를 누르면 보호 라우트가 로그인 화면으로 보낸다.
 *
 * 디자인: 위쪽이 물결로 찢긴 크림색 종이 띠(torn-top) + 메뉴마다 손그림 스티커.
 * 고른 메뉴는 연한 핑크 스티커 배경으로 강조한다 (스티커 이미지는 1장, 강조는 CSS).
 */
export default function BottomNav() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)

  const items: { to: string; label: string; sticker: StickerKey; end: boolean }[] = [
    { to: '/', label: '홈', sticker: 'navHome', end: true },
    { to: '/nearby', label: '주변 탐색', sticker: 'navNearby', end: false },
    { to: '/mypage/courses', label: '저장한 코스', sticker: 'navSaved', end: false },
    isLoggedIn
      ? { to: '/mypage', label: '마이', sticker: 'navMy', end: true }
      : { to: '/login', label: '로그인', sticker: 'navMy', end: true },
  ]

  return (
    <nav className="torn-top sticky bottom-0 z-20 grid grid-cols-4 gap-1 bg-cream px-2 pt-3 pb-[max(env(safe-area-inset-bottom),0.5rem)] drop-shadow-[0_-2px_4px_rgba(32,32,32,0.08)]">
      {items.map((item) => (
        <NavLink
          key={item.label}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            `flex h-16 flex-col items-center justify-center gap-0.5 rounded-2xl border-2 text-[11px] font-bold ${
              isActive ? 'border-primary bg-primary-light text-ink' : 'border-transparent text-neutral-600'
            }`
          }
        >
          <StickerImage name={item.sticker} className="size-9" />
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
