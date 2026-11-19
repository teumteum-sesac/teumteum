import { useEffect, useId, useRef, useState } from 'react'
import { getCourseDirectionsUrl } from '../courseDirections'
import type { Course } from '../types'

/** 코스의 출발지·경유지·도착지를 입력한 차량·도보 길찾기. */
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
      const top = actionBar?.getBoundingClientRect().top ?? window.innerHeight - 84
      const bottom = Math.max(96, window.innerHeight - top + 8)
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

  if (!url) return null

  const toggleButton = (
    <button
      type="button"
      aria-expanded={expanded}
      aria-controls={contentId}
      aria-label={expanded ? '코스 길찾기 접기' : '코스 길찾기 펼치기'}
      onClick={() => setExpanded(!expanded)}
      className="flex size-10 items-center justify-center rounded-lg text-ink focus-visible:outline-2 focus-visible:outline-primary"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" className="size-7" aria-hidden="true">
        <path d="M4 6h16M4 12h16M4 18h16" />
      </svg>
    </button>
  )

  return (
    <>
    <div className="-mt-9 mb-3 flex justify-end">{toggleButton}</div>
    <aside
      ref={sidebarRef}
      aria-label="코스 길찾기"
      hidden={!expanded}
      className="fixed top-16 left-1/2 z-40 w-full max-w-120 -translate-x-1/2 overflow-hidden border border-line bg-white shadow-xl"
      style={{ bottom: 'var(--directions-bottom, 96px)' }}
    >
      <div id={contentId} inert={!expanded} className="flex h-full flex-col">
      <header className="flex shrink-0 items-center justify-between border-b border-line bg-cream px-3 py-1">
        <h2 className="font-display text-lg">코스 길찾기 · 카카오맵</h2>
        {toggleButton}
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
