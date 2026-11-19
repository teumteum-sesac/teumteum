import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import Icon from './Icon'

interface ArrowScrollRowProps {
  children: ReactNode
  /** 가로 스크롤 영역에 더할 클래스 (예: 'flex gap-2 py-1') */
  className?: string
  /** 화살표 버튼 읽기 문구 (예: '카테고리' → '이전 카테고리 보기') */
  itemName?: string
}

/** 이만큼(px) 이하로 남았으면 끝에 닿은 것으로 본다 (소수점 스크롤 오차) */
const EDGE = 2

/*
 * [이 파일이 하는 일]
 * 가로로 넘기는 한 줄 + 양 끝 < > 버튼. 넘길 내용이 있는 쪽에만 버튼이 보인다.
 * 조금만 잘려 있어도 버튼이 나오고, 누르면 그쪽 끝까지 한 번에 넘긴다 (> 는 오른쪽 끝, < 는 왼쪽 끝)
 * 쓰는 곳: 대분류 칩 줄(ChipGroup arrows), 코스 지도 지점 칩 줄(CourseMapSheet)
 */
export default function ArrowScrollRow({ children, className = '', itemName = '항목' }: ArrowScrollRowProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [edge, setEdge] = useState({ left: false, right: false })

  const update = useCallback(() => {
    const el = ref.current
    if (!el) return
    const left = el.scrollLeft > EDGE
    const right = el.scrollLeft + el.clientWidth < el.scrollWidth - EDGE
    setEdge((prev) => (prev.left === left && prev.right === right ? prev : { left, right }))
  }, [])

  // 처음 그릴 때, 화면·내용 크기가 바뀔 때 다시 잰다
  useEffect(() => {
    const el = ref.current
    if (!el) return
    update()
    const observer = new ResizeObserver(update)
    observer.observe(el)
    for (const child of el.children) observer.observe(child)
    return () => observer.disconnect()
  }, [update, children])

  const move = (dir: -1 | 1) => {
    const el = ref.current
    el?.scrollTo({ left: dir < 0 ? 0 : el.scrollWidth, behavior: 'smooth' })
  }

  return (
    <div className="relative">
      <div ref={ref} onScroll={update} className={`overflow-x-auto ${className}`}>
        {children}
      </div>
      {edge.left && <ArrowButton dir={-1} label={`이전 ${itemName} 보기`} onClick={() => move(-1)} />}
      {edge.right && <ArrowButton dir={1} label={`다음 ${itemName} 보기`} onClick={() => move(1)} />}
    </div>
  )
}

function ArrowButton({ dir, label, onClick }: { dir: -1 | 1; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={`absolute top-1/2 z-10 grid size-7 -translate-y-1/2 cursor-pointer place-items-center rounded-full border border-line bg-white text-ink shadow-[0_1px_4px_rgba(0,0,0,0.12)] ${
        dir < 0 ? 'left-0' : 'right-0'
      }`}
    >
      <Icon name={dir < 0 ? 'chevronLeft' : 'chevronRight'} className="size-4" />
    </button>
  )
}
