import { Outlet } from 'react-router'
import { PAPER_STYLE } from '../assets/stickerAssets'

// 모바일 우선: 기본은 모바일 폭(최대 480px), PC에서는 가운데 정렬.
// 화면마다 AppHeader를 두고, 하단바는 홈·주변 탐색에만 고정한다. (화면설계서 v2.0)
// 배경: 크림색 + 종이 질감 타일(texture-paper 에셋이 있을 때만). PC 바깥 여백은 body 의 종이 베이지.
export default function Layout() {
  return (
    <div
      className="mx-auto flex min-h-dvh max-w-120 flex-col bg-cream"
      style={PAPER_STYLE}
    >
      <Outlet />
    </div>
  )
}
