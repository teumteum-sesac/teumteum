import { NavLink } from 'react-router'
import { PAPER_STYLE, type StickerKey } from '../assets/stickerAssets'
import { useAuthStore } from '../auth/authStore'
import StickerImage from '../components/StickerImage'

/**
 * 하단바: 홈·주변 탐색 화면에만 고정 (화면설계서 v2.0)
 * 비로그인 상태에서 '저장한 코스'를 누르면 보호 라우트가 로그인 화면으로 보낸다.
 *
 * 디자인: 위쪽이 물결로 찢긴 크림색 종이 띠(torn-top) + 메뉴마다 손그림 스티커.
 * 고른 메뉴는 연한 핑크 스티커 배경 + 살짝 기울여 "붙인" 느낌으로 강조한다 (스티커 이미지는 1장, 강조는 CSS).
 * 그림자는 바깥 nav 에, 물결 자르기는 안쪽 div 에 준다 (같은 요소에 주면 그림자까지 잘려 나간다).
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
    <nav className="sticky bottom-0 z-20 drop-shadow-[0_-3px_6px_rgba(32,32,32,0.1)]">
      <div
        className="torn-top grid grid-cols-4 gap-1 bg-cream px-2 pt-3.5 pb-[max(env(safe-area-inset-bottom),0.5rem)]"
        style={PAPER_STYLE}
      >
        {items.map((item) => (
          <NavLink
            key={item.label}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex h-16 flex-col items-center justify-center gap-0.5 rounded-2xl border-2 text-[11px] font-bold transition-transform ${
                isActive ? '-rotate-2 border-primary bg-primary-light text-ink' : 'border-transparent text-neutral-600'
              }`
            }
          >
            <StickerImage name={item.sticker} className="size-9" />
            {item.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
