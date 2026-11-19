import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router'
import { useGoBack } from '../hooks/useGoBack'
import Icon from './Icon'

interface AppHeaderProps {
  title?: ReactNode
  showBack?: boolean
  /** 뒤로 갈 앱 기록이 없을 때(주소 직접 입력·새 탭) 갈 화면. 기본 홈 */
  backFallback?: string
  /** 오른쪽 홈 버튼. 하단바가 있는 화면은 false (하단바에 홈이 있음) */
  showHome?: boolean
  /** 우측 액션 (프로필·설정·찜 등). 주면 홈 버튼 대신 이것을 보여 준다 */
  right?: ReactNode
}

/**
 * 화면 상단 바. 제목은 가운데(목업 기준), 뒤로가기는 왼쪽, 홈 버튼(또는 액션)은 오른쪽
 * - 뒤로가기: 이전에 보던 화면으로 (useGoBack). 첫 화면이면 backFallback 으로
 * - 홈 버튼: 하단바가 없는 화면에서도 한 번에 홈으로 갈 수 있게
 */
export default function AppHeader({ title, showBack = true, backFallback = '/', showHome = true, right }: AppHeaderProps) {
  const goBack = useGoBack(backFallback)
  const isHome = useLocation().pathname === '/'

  return (
    <header className="sticky top-0 z-10 flex h-14 items-center bg-cream/95 px-2 backdrop-blur">
      <div className="flex w-12 justify-start">
        {showBack && (
          <button type="button" aria-label="뒤로가기" onClick={goBack} className="flex size-10 items-center justify-center">
            {/* 아이콘은 이미지 에셋이 아니라 코드(SVG)로 그린다: 작은 기능 아이콘은 선명함·색 변경이 중요 */}
            <svg viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M15 5l-7 7 7 7" />
            </svg>
          </button>
        )}
      </div>
      <h1 className="flex-1 truncate text-center font-display text-xl">{title}</h1>
      <div className="flex w-12 items-center justify-end">
        {right ??
          (showHome && !isHome && (
            <Link to="/" aria-label="홈으로" className="flex size-10 items-center justify-center">
              <Icon name="home" className="size-6" strokeWidth={2.3} />
            </Link>
          ))}
      </div>
    </header>
  )
}
