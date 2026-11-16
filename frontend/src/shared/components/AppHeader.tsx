import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'

interface AppHeaderProps {
  title?: ReactNode
  showBack?: boolean
  /** 우측 액션 (프로필·설정·찜 등) */
  right?: ReactNode
}

/** 화면 상단 바. 제목은 가운데(목업 기준), 뒤로가기는 왼쪽, 액션은 오른쪽 */
export default function AppHeader({ title, showBack = true, right }: AppHeaderProps) {
  const navigate = useNavigate()

  return (
    <header className="sticky top-0 z-10 flex h-14 items-center bg-cream/95 px-2 backdrop-blur">
      <div className="flex w-12 justify-start">
        {showBack && (
          <button
            type="button"
            aria-label="뒤로가기"
            onClick={() => navigate(-1)}
            className="flex size-10 items-center justify-center"
          >
            {/* 아이콘은 이미지 에셋이 아니라 코드(SVG)로 그린다: 작은 기능 아이콘은 선명함·색 변경이 중요 */}
            <svg viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M15 5l-7 7 7 7" />
            </svg>
          </button>
        )}
      </div>
      <h1 className="flex-1 truncate text-center font-display text-xl">{title}</h1>
      <div className="flex w-12 items-center justify-end">{right}</div>
    </header>
  )
}
