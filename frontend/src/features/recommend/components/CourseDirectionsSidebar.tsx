import { useEffect, useId, useRef, useState } from 'react'
import Icon from '../../../shared/components/Icon'
import { getCourseDirectionsUrl } from '../courseDirections'
import type { Course } from '../types'

/**
 * 코스의 출발지·경유지·도착지를 입력한 차량·도보 길찾기 (카카오맵 화면을 창으로 띄움).
 * 코스 타임라인 제목 오른쪽 [카카오맵 길찾기] 로 열고, 창 오른쪽 위 [닫기](또는 Esc)로 닫는다
 */
export default function CourseDirectionsSidebar({ course }: { course: Course }) {
  const [expanded, setExpanded] = useState(false)
  const sidebarRef = useRef<HTMLElement>(null)
  const contentId = useId()
  const url = getCourseDirectionsUrl(course)

  useEffect(() => {
    const sidebar = sidebarRef.current
    const actionBar = document.querySelector('[data-bottom-action-bar]')
    if (!sidebar) return
    const update = () => {
      const top = actionBar?.getBoundingClientRect().top ?? window.innerHeight
      // 하단 버튼 줄 바로 위까지 (틈 없이)
      const bottom = Math.max(0, window.innerHeight - top)
      sidebar.style.setProperty('--directions-bottom', `${bottom}px`)
    }
    update()
    const observer = new ResizeObserver(update)
    if (actionBar) observer.observe(actionBar)
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', update)
      window.removeEventListener('scroll', update, true)
    }
  }, [url])

  // 열려 있을 때 Esc 로 닫는다
  useEffect(() => {
    if (!expanded) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setExpanded(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [expanded])

  if (!url) return null

  // 여는 버튼: 무엇이 열리는지 바로 알 수 있게 지도 아이콘 + '카카오맵 길찾기' 글자
  const openButton = (
    <button
      type="button"
      aria-expanded={expanded}
      aria-controls={contentId}
      onClick={() => setExpanded(true)}
      className="flex h-8 items-center gap-1 rounded-full border-2 border-ink bg-sunny px-3 text-xs font-bold text-ink shadow-[0_2px_0_var(--color-ink)] focus-visible:outline-2 focus-visible:outline-primary active:translate-y-0.5 active:shadow-none"
    >
      <Icon name="map" className="size-4" strokeWidth={2.4} />
      카카오맵 길찾기
    </button>
  )

  return (
    <>
    <div className="-mt-10 mb-3 flex justify-end">{openButton}</div>
    <aside
      ref={sidebarRef}
      aria-label="코스 길찾기"
      hidden={!expanded}
      className="fixed top-14 left-1/2 z-40 w-full max-w-120 -translate-x-1/2 overflow-hidden border border-line bg-white shadow-xl"
      style={{ bottom: 'var(--directions-bottom, 0px)' }}
    >
      <div id={contentId} inert={!expanded} className="flex h-full flex-col">
      <header className="flex shrink-0 items-center justify-between border-b border-line bg-cream px-3 py-1">
        <h2 className="font-display text-lg">코스 길찾기 · 카카오맵</h2>
        {/* 닫기: 열리자마자 오른쪽 위에 보인다. Esc 로도 닫힌다 */}
        <button
          type="button"
          onClick={() => setExpanded(false)}
          aria-label="코스 길찾기 닫기"
          className="flex h-9 items-center gap-1 rounded-full border-2 border-ink bg-white pr-3 pl-2 text-sm font-bold text-ink focus-visible:outline-2 focus-visible:outline-primary"
        >
          <Icon name="close" className="size-4" strokeWidth={2.8} />
          닫기
        </button>
      </header>
      <iframe
        key={url}
        src={url}
        title="코스 출발지·경유지·도착지 카카오맵 길찾기"
        className="block min-h-0 w-full flex-1 border-0"
      />
      </div>
    </aside>
    </>
  )
}
