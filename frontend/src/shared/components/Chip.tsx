import type { ReactNode } from 'react'
import Icon from './Icon'

interface ChipProps {
  selected?: boolean
  onClick?: () => void
  /** main = 대분류처럼 위 단계 (기본) / sub = 세분류처럼 아래 단계 (더 작고 가볍게) */
  level?: 'main' | 'sub'
  children: ReactNode
}

/**
 * 카테고리·반경·세부 카테고리 등 선택용 칩. 단일/복수 선택은 쓰는 쪽에서 정한다.
 * 쓰는 곳: 추천 결과 대분류(main)·세분류(sub) 필터(RES-01), 주변 탐색 카테고리(HOM-02, main)
 *
 * 색은 파스텔 핑크 계열 톤온톤으로 맞추고, 선택 여부는 색만이 아니라 ✓ 체크·테두리·굵기로 함께 보여 준다.
 *   main 선택됨 : 연한 핑크 + 잉크 테두리·아래 그림자(스티커 버튼처럼 도드라짐) + ✓
 *   sub  선택됨 : 흰 바탕 + 핑크 테두리 + 진한 핑크 글씨 + ✓ (그림자 없이 한 단계 가볍게)
 *   안 선택     : main 은 크림 + 종이색 테두리, sub 는 흰 바탕 + 옅은 테두리
 * 고르는 순간 살짝 통 튄다 (index.css chip-pop)
 */
const STYLE = {
  main: {
    base: 'h-9 px-3.5 text-sm',
    on: 'chip-pop border-ink bg-primary-light font-bold text-ink shadow-[0_2px_0_var(--color-ink)]',
    off: 'border-line bg-cream text-neutral-600',
  },
  sub: {
    base: 'h-7.5 px-3 text-xs',
    on: 'chip-pop border-primary bg-white font-bold text-primary-dark',
    off: 'border-primary-light bg-white/70 text-neutral-500',
  },
} as const

export default function Chip({ selected = false, onClick, level = 'main', children }: ChipProps) {
  const style = STYLE[level]
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`flex shrink-0 items-center gap-1 rounded-full border-2 whitespace-nowrap transition-colors ${style.base} ${
        selected ? style.on : style.off
      }`}
    >
      {selected && <Icon name="check" className={level === 'main' ? '-ml-1 size-4' : '-ml-0.5 size-3.5'} strokeWidth={3} />}
      {children}
    </button>
  )
}
