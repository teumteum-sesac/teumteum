import { useState } from 'react'
import { NavLink } from 'react-router'
import { PAPER_STYLE, type StickerKey } from '../assets/stickerAssets'
import { useAuthStore } from '../auth/authStore'
import LoginRequiredDialog from '../auth/LoginRequiredDialog'
import StickerImage from '../components/StickerImage'

/**
 * 하단바: 탭 화면 4개(홈·주변 탐색·저장한 코스·마이페이지)에 고정. 어느 탭에서든 다른 탭으로 바로 간다.
 * 아래쪽에 자기 버튼 줄(추천받기·저장 등)이 있는 화면에는 두지 않고, 대신 상단 홈 버튼(AppHeader)으로 홈에 간다.
 * 마이 탭은 비회원에게도 [마이]로 보인다. 비회원이 누르면 마이페이지의 로그인 안내 화면이 나온다 (바로 로그인 화면 X)
 * 비로그인 상태에서 '저장한 코스'를 누르면 화면을 옮기지 않고 "로그인이 필요한 서비스입니다" 확인 창을 띄운다.
 *   [로그인] → 로그인 화면 → 로그인 후 저장한 코스로 복귀 / [취소] → 지금 화면 그대로
 *
 * 디자인: 위쪽이 물결로 찢긴 크림색 종이 띠(torn-top) + 메뉴마다 손그림 스티커.
 * 고른 메뉴는 연한 핑크 스티커 배경 + 살짝 기울여 "붙인" 느낌으로 강조한다 (스티커 이미지는 1장, 강조는 CSS).
 * 그림자는 바깥 nav 에, 물결 자르기는 안쪽 div 에 준다 (같은 요소에 주면 그림자까지 잘려 나간다).
 */
export default function BottomNav() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  // 비회원이 회원 전용 메뉴를 눌렀을 때 띄울 확인 창 (로그인 후 돌아갈 주소). null 이면 닫힘
  const [askLoginFor, setAskLoginFor] = useState<string | null>(null)

  const items: { to: string; label: string; sticker: StickerKey; end: boolean; memberOnly?: boolean }[] = [
    { to: '/', label: '홈', sticker: 'navHome', end: true },
    { to: '/nearby', label: '주변 탐색', sticker: 'navNearby', end: false },
    { to: '/mypage/courses', label: '저장한 코스', sticker: 'navSaved', end: false, memberOnly: true },
    { to: '/mypage', label: '마이', sticker: 'navMy', end: true },
  ]

  return (
    <>
      <nav className="sticky bottom-0 z-20 mt-auto drop-shadow-[0_-3px_6px_rgba(32,32,32,0.1)]">
        <div
          className="torn-top grid grid-cols-4 gap-1 bg-cream px-2 pt-3.5 pb-[max(env(safe-area-inset-bottom),0.5rem)]"
          style={PAPER_STYLE}
        >
          {items.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.end}
              onClick={(e) => {
                if (item.memberOnly && !isLoggedIn) {
                  e.preventDefault() // 로그인 화면으로 바로 가지 않고 먼저 물어본다
                  setAskLoginFor(item.to)
                }
              }}
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
      {/* nav 바깥에 둔다: nav 의 drop-shadow(filter) 안에 있으면 fixed 창이 화면 전체가 아니라 nav 기준으로 붙는다 */}
      {askLoginFor && <LoginRequiredDialog from={askLoginFor} onClose={() => setAskLoginFor(null)} />}
    </>
  )
}
