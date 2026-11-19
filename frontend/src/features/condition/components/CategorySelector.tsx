import { useCallback, useState } from 'react'
import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import SegmentedControl from '../../../shared/components/SegmentedControl'
import StickerImage from '../../../shared/components/StickerImage'
import Toast from '../../../shared/components/Toast'
import { CATEGORIES, MAX_CATEGORIES, type CategoryCode, type CategoryMode } from '../../../shared/types/domain'
import { useConditionStore } from '../store'

const MODE_OPTIONS: { value: CategoryMode; label: string }[] = [
  { value: 'RANDOM', label: '랜덤 추천' },
  { value: 'DIRECT', label: '직접 선택' },
]

/**
 * 카테고리 (CON-01 ④): 랜덤 추천(기본) 또는 직접 선택(대분류 최대 3개). 세분류는 여기서 고르지 않는다.
 * - 아래 타일을 하나라도 고르면 '직접 선택'에 불이 들어오고 '랜덤 추천'은 꺼진다
 * - 타일을 전부 풀거나 '랜덤 추천'을 누르면 고른 카테고리가 모두 풀리고 랜덤 추천으로 돌아간다
 * - 아무것도 안 고른 채 '직접 선택'을 누르면 타일을 골라 달라고 안내만 한다 (안 고르면 랜덤이므로)
 * - 4개째를 누르면 "최대 3개까지 고를 수 있어요" 토스트
 * 고른 개수는 제목 줄 '최대 3개' 오른쪽에 보인다 (ConditionForm)
 * 디자인 (목업 CON-01): 4열 스티커 타일. 고른 타일은 핑크 + 살짝 기울여 "붙인" 느낌
 */
export default function CategorySelector() {
  const { categories, setCategoryMode, toggleCategory } = useConditionStore()
  const [toast, setToast] = useState<string | null>(null)
  const closeToast = useCallback(() => setToast(null), [])
  const mode: CategoryMode = categories.length > 0 ? 'DIRECT' : 'RANDOM'

  const handleMode = (next: CategoryMode) => {
    if (next === 'DIRECT' && categories.length === 0) {
      setToast('아래에서 카테고리를 골라 주세요')
      return
    }
    setCategoryMode(next)
  }

  const handleToggle = (code: CategoryCode) => {
    if (!categories.includes(code) && categories.length >= MAX_CATEGORIES) {
      setToast(`최대 ${MAX_CATEGORIES}개까지 고를 수 있어요`)
      return
    }
    toggleCategory(code)
  }

  return (
    <div className="flex flex-col gap-3">
      <SegmentedControl options={MODE_OPTIONS} value={mode} onChange={handleMode} />

      <div className="grid grid-cols-4 gap-2">
        {CATEGORIES.map((c) => {
          const selected = categories.includes(c.code)
          return (
            <button
              key={c.code}
              type="button"
              aria-pressed={selected}
              onClick={() => handleToggle(c.code)}
              className={`flex flex-col items-center gap-1 rounded-2xl border-2 px-1 pt-2 pb-1.5 transition-transform ${
                selected ? '-rotate-2 border-primary bg-primary-light' : 'border-line bg-cream'
              }`}
            >
              <StickerImage name={CATEGORY_STICKER[c.code]} className="size-10" />
              <span className={`text-[11px] leading-tight ${selected ? 'font-bold text-ink' : 'text-neutral-600'}`}>{c.label}</span>
            </button>
          )
        })}
      </div>

      {toast && <Toast message={toast} onClose={closeToast} />}
    </div>
  )
}
