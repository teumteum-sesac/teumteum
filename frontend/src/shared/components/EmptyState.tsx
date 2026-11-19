import type { ReactNode } from 'react'
import type { StickerKey } from '../assets/stickerAssets'
import StickerImage from './StickerImage'

interface EmptyStateProps {
  message: string
  /** 안내 아래 보조 설명 */
  description?: string
  /** 위에 그릴 장면 일러스트 (예: 추천 결과 없음 → 'sceneEmpty') */
  sticker?: StickerKey
  action?: ReactNode
}

/** 결과 없음·목록 비어 있음 안내. 실패처럼 보이지 않게 캐릭터 장면 + 친근한 문구로 보여 준다 */
export default function EmptyState({ message, description, sticker, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
      {sticker && <StickerImage name={sticker} className="w-60" ratio="aspect-[3/2]" />}
      <p className={sticker ? 'font-display text-xl' : 'text-sm text-neutral-500'}>{message}</p>
      {description && <p className="text-sm text-neutral-500">{description}</p>}
      {action && <div className="mt-2 flex w-full flex-col gap-2">{action}</div>}
    </div>
  )
}
