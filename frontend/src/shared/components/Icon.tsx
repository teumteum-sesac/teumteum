import type { SVGProps } from 'react'

/*
 * [이 파일이 하는 일]
 * 작은 기능 아이콘(검색·화살표·하트·시계 등)을 SVG 로 그린다.
 * 스티커 이미지가 아니라 코드로 그리는 이유: 작은 크기에서 선명하고, 글자색(currentColor)을 그대로 따라가서
 * 선택 상태 등에 맞춰 색을 바꾸기 쉽다. (docs/design-sticker.md 3장)
 *
 *   <Icon name="search" className="size-5" />
 */
const PATHS = {
  home: <><path d="M4 11.5L12 5l8 6.5" /><path d="M6.5 10v9h11v-9" /><path d="M10 19v-4.5h4V19" /></>,
  search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
  chevronRight: <path d="M9 5l7 7-7 7" />,
  chevronLeft: <path d="M15 5l-7 7 7 7" />,
  chevronDown: <path d="M5 9l7 7 7-7" />,
  clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
  heart: <path d="M12 20s-7-4.4-7-10a4 4 0 017-2.6A4 4 0 0119 10c0 5.6-7 10-7 10z" />,
  bookmark: <path d="M7 4h10v16l-5-3.5L7 20z" />,
  refresh: <><path d="M19 12a7 7 0 11-2.05-4.95" /><path d="M19 4v4h-4" /></>,
  sliders: <><path d="M4 7h10M18 7h2M4 17h4M12 17h8" /><circle cx="16" cy="7" r="2" /><circle cx="10" cy="17" r="2" /></>,
  location: <><path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0113 0c0 4.8-6.5 11-6.5 11z" /><circle cx="12" cy="10" r="2.3" /></>,
  flag: <><path d="M6 21V4" /><path d="M6 4h11l-2.5 4L17 12H6" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8" /></>,
  user: <><circle cx="12" cy="8.5" r="3.5" /><path d="M5 20a7 7 0 0114 0" /></>,
  close: <path d="M6 6l12 12M18 6L6 18" />,
  check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
  logout: <><path d="M14 4h4.5v16H14" /><path d="M9.5 8l-4 4 4 4" /><path d="M5.5 12h10" /></>,
  map: <><path d="M9 4L3.5 6v14L9 18l6 2 5.5-2V4L15 6z" /><path d="M9 4v14M15 6v14" /></>,
} as const

export type IconName = keyof typeof PATHS

interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName
  /** 선 굵기 (기본 2.2: 스티커 외곽선과 어울리게 조금 굵게) */
  strokeWidth?: number
}

export default function Icon({ name, className = 'size-5', strokeWidth = 2.2, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className={`shrink-0 ${className}`}
      {...props}
    >
      {PATHS[name]}
    </svg>
  )
}
