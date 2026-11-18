import { useEffect, useId, useRef, useState } from 'react'

interface LocationGuideProps {
  /** 지도 영역 위쪽에서 카드 위끝까지(px). 검색창 바로 아래 + 12px */
  top: number
  onClose: () => void
  /** 위치를 다시 요청한다. 성공하면 HomeScreen 이 카드를 닫고, 실패하면 카드를 그대로 둔다 */
  onRetry: () => Promise<unknown>
}

/**
 * 위치 안내 카드 (HOM-01 내 위치 버튼 '위치 꺼짐' 상태에서 누름).
 * 웹은 설정 화면을 직접 열 수 없어 '설정 열기' 버튼은 두지 않는다. 사용자가 읽고 고를 수 있게 자동으로 사라지지 않는다
 */
export default function LocationGuide({ top, onClose, onRetry }: LocationGuideProps) {
  const retryRef = useRef<HTMLButtonElement>(null)
  const [retrying, setRetrying] = useState(false)
  const titleId = useId()
  const descId = useId()

  // 열리면 '다시 시도'로 포커스, Esc 로 닫기
  useEffect(() => {
    retryRef.current?.focus()
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose])

  const retry = () => {
    setRetrying(true)
    void onRetry().finally(() => {
      setRetrying(false)
      // 실패해서 카드가 남아 있으면 포커스를 다시 버튼에 둔다
      retryRef.current?.focus()
    })
  }

  return (
    <div
      role="alertdialog"
      aria-labelledby={titleId}
      aria-describedby={descId}
      style={{ top }}
      className="absolute inset-x-4 z-[35] rounded-2xl bg-[#2B2420] py-3.5 pr-3.5 pl-4 text-white shadow-[0_4px_12px_rgba(0,0,0,0.25)]"
    >
      <p id={titleId} className="text-[15px] font-bold">
        위치를 확인할 수 없어요
      </p>
      <p id={descId} className="mt-1 text-[13px] leading-snug text-white/80">
        브라우저나 휴대폰 설정에서 위치 권한을 허용하면 내 위치 주변으로 코스를 찾아드려요.
      </p>
      <div className="mt-3 flex justify-end gap-2">
        <button
          type="button"
          onClick={onClose}
          className="min-h-10 rounded-xl border-2 border-white/70 px-4 text-sm font-bold focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#D63F7C]"
        >
          닫기
        </button>
        <button
          ref={retryRef}
          type="button"
          onClick={retry}
          disabled={retrying}
          aria-busy={retrying}
          className="min-h-10 rounded-xl border-2 border-[#2B2420] bg-[#FFD84D] px-4 text-sm font-bold text-[#2B2420] disabled:opacity-70 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#D63F7C]"
        >
          {retrying ? '확인 중…' : '다시 시도'}
        </button>
      </div>
    </div>
  )
}
