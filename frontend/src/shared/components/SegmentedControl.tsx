import type { StickerKey } from '../assets/stickerAssets'
import StickerImage from './StickerImage'

interface SegmentedControlProps<T extends string> {
  options: { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
  /** 값별 스티커 (예: 이동수단 TRANSPORT_STICKER). 주면 글자 위에 그린다 */
  stickers?: Partial<Record<T, StickerKey>>
}

/** 이동수단 선택 같은 버튼 묶음. 각 버튼이 따로 떨어진 종이 카드 모양이고, 고른 것은 핑크 */
export default function SegmentedControl<T extends string>({ options, value, onChange, stickers }: SegmentedControlProps<T>) {
  return (
    <div className="flex gap-2">
      {options.map((option) => {
        const selected = option.value === value
        const sticker = stickers?.[option.value]
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option.value)}
            className={`flex flex-1 flex-col items-center justify-center gap-1 rounded-2xl border-2 text-sm ${sticker ? 'h-18' : 'h-11'} ${
              selected ? 'border-primary bg-primary-light font-bold text-ink' : 'border-line bg-cream text-neutral-600'
            }`}
          >
            {sticker && <StickerImage name={sticker} className="size-8" />}
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
