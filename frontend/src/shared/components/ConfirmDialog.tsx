import { PAPER_STYLE } from '../assets/stickerAssets'
import Button from './Button'
import StickerImage from './StickerImage'

interface ConfirmDialogProps {
  message: string
  /** 메시지 아래 보조 설명 */
  description?: string
  confirmLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

/** 페이지 안에서 한 번 더 확인하는 창 (저장 해제·로그아웃 등). 브라우저 confirm 대신 쓴다 */
export default function ConfirmDialog({ message, description, confirmLabel = '확인', onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <div role="dialog" aria-modal="true" className="fixed inset-0 z-40 flex items-end justify-center bg-ink/40 p-4 sm:items-center">
      {/* 종이 카드 + 위에서 빼꼼 내민 거북이 */}
      <div className="relative w-full max-w-112 rounded-3xl border-2 border-ink bg-cream p-5 pt-6 shadow-[0_4px_0_var(--color-ink)]" style={PAPER_STYLE}>
        <StickerImage name="turtlePeek" className="pointer-events-none absolute -top-14 right-4 w-24" ratio="aspect-[3/2]" />
        <p className="font-display text-xl">{message}</p>
        {description && <p className="mt-1 text-sm text-neutral-500">{description}</p>}
        <div className="mt-5 flex gap-2">
          <Button variant="secondary" onClick={onCancel}>
            취소
          </Button>
          <Button variant="pink" onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
