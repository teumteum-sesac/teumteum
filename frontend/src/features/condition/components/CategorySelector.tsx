import { useCallback, useState } from 'react'
import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import SegmentedControl from '../../../shared/components/SegmentedControl'
import StickerImage from '../../../shared/components/StickerImage'
import Toast from '../../../shared/components/Toast'
import { CATEGORIES, MAX_CATEGORIES, type CategoryCode, type CategoryMode } from '../../../shared/types/domain'
import { useConditionStore } from '../store'

const MODE_OPTIONS: { value: CategoryMode; label: string }[] = [
  { value: 'DIRECT', label: '직접 선택' },
  { value: 'RANDOM', label: '랜덤 추천' },
]

/**
 * 카테고리 (CON-01 ④): 직접 선택(대분류 최대 3개) 또는 랜덤 추천. 세분류는 여기서 고르지 않는다.
 * 4개째를 누르면 "최대 3개까지 고를 수 있어요" 토스트
 * 디자인 (목업 CON-01): 4열 스티커 타일. 고른 타일은 핑크 + 살짝 기울여 "붙인" 느낌
 */
export default function CategorySelector() {
  const { categoryMode, categories, setCategoryMode, toggleCategory } = useConditionStore()
  const [toast, setToast] = useState(false)
  const closeToast = useCallback(() => setToast(false), [])

  const handleToggle = (code: CategoryCode) => {
    if (!categories.includes(code) && categories.length >= MAX_CATEGORIES) {
      setToast(true)
      return
    }
    toggleCategory(code)
  }

  return (
    <div className="flex flex-col gap-3">
      <SegmentedControl options={MODE_OPTIONS} value={categoryMode} onChange={setCategoryMode} />

      {categoryMode === 'DIRECT' ? (
        <>
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
          <p className="px-1 text-xs text-neutral-400">
            {categories.length === 0 ? '아무것도 고르지 않으면 랜덤으로 추천해요.' : `${categories.length} / ${MAX_CATEGORIES}개 골랐어요`}
          </p>
        </>
      ) : (
        <div className="paper-card flex items-center gap-3 px-4 py-3">
          <StickerImage name="rabbitHead" className="size-10" />
          <p className="text-sm text-neutral-600">카테고리 없이 조건에 맞는 곳을 토끼가 골라 올게요!</p>
        </div>
      )}

      {toast && <Toast message={`최대 ${MAX_CATEGORIES}개까지 고를 수 있어요`} onClose={closeToast} />}
    </div>
  )
}
