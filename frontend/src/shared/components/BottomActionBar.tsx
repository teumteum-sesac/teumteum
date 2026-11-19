import type { ReactNode } from 'react'

/** 화면 하단 고정 버튼 영역 (버튼 1~2개) */
export default function BottomActionBar({ children }: { children: ReactNode }) {
  return <div data-bottom-action-bar className="sticky bottom-0 mt-auto flex gap-2 border-t-2 border-line bg-cream/95 p-4 backdrop-blur">{children}</div>
}
