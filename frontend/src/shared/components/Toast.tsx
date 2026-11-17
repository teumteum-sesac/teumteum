import { useEffect } from 'react'
import { Link } from 'react-router'
import StickerImage from './StickerImage'

interface ToastProps {
  message: string
  /** 메시지 옆 링크 (예: '저장한 코스 보기') */
  action?: { label: string; to: string }
  onClose: () => void
  /** 자동으로 닫히기까지 시간(ms) */
  duration?: number
}

/** 잠깐 떴다 사라지는 알림. 디자인: 잉크색 쪽지 + 왼쪽에 반짝이 스티커 */
export default function Toast({ message, action, onClose, duration = 3000 }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(onClose, duration)
    return () => clearTimeout(timer)
  }, [onClose, duration])

  return (
    <div
      role="status"
      className="fixed inset-x-0 bottom-24 z-30 mx-auto flex w-[calc(100%-2rem)] max-w-112 items-center gap-2.5 rounded-2xl border-2 border-ink bg-ink px-3.5 py-3 text-sm font-bold text-cream shadow-[0_4px_0_rgba(32,32,32,0.25)]"
    >
      <StickerImage name="decoSparkle" className="size-6" />
      <span className="flex-1">{message}</span>
      {action && (
        <Link to={action.to} className="shrink-0 rounded-full bg-sunny px-2.5 py-1 text-xs text-ink">
          {action.label}
        </Link>
      )}
    </div>
  )
}
