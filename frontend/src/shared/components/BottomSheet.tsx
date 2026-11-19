import { useId, useLayoutEffect, useRef, useState, type PointerEvent, type ReactNode, type RefObject } from 'react'

interface BottomSheetProps {
  title: string
  children: ReactNode
  /** 첫 표시 위치의 기준 요소. 시트 윗부분을 이 요소 바로 아래에 맞춘다. */
  initialTopRef?: RefObject<HTMLElement | null>
}

const HEIGHTS = [0, 50, 90]
const COLLAPSED_HEIGHT = 32

function nearestHeight(height: number, viewport: number): number {
  const distance = (candidate: number) => Math.abs((candidate || COLLAPSED_HEIGHT / viewport * 100) - height)
  return HEIGHTS.reduce((best, candidate) => distance(candidate) < distance(best) ? candidate : best)
}

/** 배경 화면을 계속 조작할 수 있는 바텀시트. 손잡이로 접힘·절반·전체 높이를 조절한다. */
export default function BottomSheet({ title, children, initialTopRef }: BottomSheetProps) {
  const sheetRef = useRef<HTMLElement>(null)
  const titleId = useId()
  const [height, setHeight] = useState<number | null>(initialTopRef ? null : 50)
  const [dragging, setDragging] = useState(false)
  const drag = useRef<{ pointerId: number; y: number; height: number; viewport: number } | null>(null)

  useLayoutEffect(() => {
    const anchor = initialTopRef?.current
    const sheet = sheetRef.current
    if (!anchor || !sheet) return
    function updateInitialHeight() {
      if (!anchor || !sheet) return
      const available = window.innerHeight - anchor.getBoundingClientRect().bottom - 12
      sheet.style.setProperty('--initial-sheet-height', `${Math.max(COLLAPSED_HEIGHT, available)}px`)
    }
    updateInitialHeight()
    const observer = new ResizeObserver(updateInitialHeight)
    observer.observe(anchor.parentElement ?? anchor)
    window.addEventListener('resize', updateInitialHeight)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', updateInitialHeight)
    }
  }, [initialTopRef])

  function startDrag(event: PointerEvent<HTMLButtonElement>) {
    if (!event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) return
    const viewport = window.innerHeight
    const currentHeight = (sheetRef.current?.getBoundingClientRect().height ?? viewport * 0.5) / viewport * 100
    drag.current = { pointerId: event.pointerId, y: event.clientY, height: currentHeight, viewport }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function moveDrag(event: PointerEvent<HTMLButtonElement>) {
    const start = drag.current
    if (!start || start.pointerId !== event.pointerId) return
    if (Math.abs(start.y - event.clientY) < 8) return
    setDragging(true)
    const next = start.height + (start.y - event.clientY) / start.viewport * 100
    setHeight(Math.max(COLLAPSED_HEIGHT / start.viewport * 100, Math.min(HEIGHTS[2], next)))
  }

  function endDrag(event: PointerEvent<HTMLButtonElement>) {
    const start = drag.current
    if (!start || start.pointerId !== event.pointerId) return
    const movement = start.y - event.clientY
    if (event.type === 'pointercancel') {
      setHeight(nearestHeight(start.height, start.viewport))
    } else if (Math.abs(movement) < 8) {
      setHeight(height === 0 ? 50 : 0)
    } else {
      const next = start.height + movement / start.viewport * 100
      setHeight(nearestHeight(next, start.viewport))
    }
    drag.current = null
    setDragging(false)
  }

  return (
    <section
      ref={sheetRef}
      aria-labelledby={titleId}
      className="fixed inset-x-0 bottom-0 z-40 mx-auto w-full max-w-120 overflow-hidden rounded-t-2xl bg-white text-ink shadow-[0_-4px_16px_rgba(0,0,0,0.08)]"
      style={{ height: height === null ? 'var(--initial-sheet-height, 50dvh)' : height === 0 ? COLLAPSED_HEIGHT : `${height}dvh`, transition: dragging || height === null ? 'none' : 'height 200ms ease-out' }}
    >
      <div className="flex h-full flex-col">
        <button
          type="button"
          aria-label="정보 창 크기 조절: 위아래로 끌거나 방향키를 누르세요"
          onPointerDown={startDrag}
          onPointerMove={moveDrag}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
          onLostPointerCapture={() => {
            drag.current = null
            setDragging(false)
          }}
          aria-expanded={height !== 0}
          onClick={(event) => { if (event.detail === 0) setHeight(height === 0 ? 50 : 0) }}
          onKeyDown={(event) => {
            if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return
            event.preventDefault()
            setHeight(event.key === 'ArrowUp' ? ((height ?? 50) < 50 ? 50 : 90) : ((height ?? 50) > 50 ? 50 : 0))
          }}
          className="flex h-8 w-full shrink-0 touch-none cursor-ns-resize items-center justify-center focus-visible:outline-2 focus-visible:outline-primary"
        >
          <span className="h-1 w-10 rounded-full bg-neutral-300" />
        </button>
        <header hidden={height === 0} className="shrink-0 border-b border-line px-4 pb-3">
          <h2 id={titleId} className="min-w-0 truncate font-display text-lg">{title}</h2>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain" inert={height === 0} style={{ pointerEvents: dragging ? 'none' : undefined }}>
          {children}
        </div>
      </div>
    </section>
  )
}
