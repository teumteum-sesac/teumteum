import type { CSSProperties } from 'react'
import { TIME_STEPS } from '../../../shared/types/domain'

interface TimeSliderProps {
  value: number
  onChange: (minutes: number) => void
}

/** 막대 칸: 0(맨 왼쪽, 글자 없음) · 1h ~ 6h */
const HOUR = 60
const MAX_INDEX = TIME_STEPS[TIME_STEPS.length - 1] / HOUR
const MIN_INDEX = TIME_STEPS[0] / HOUR

/**
 * 이용 시간 슬라이더. 막대는 0부터 6시간까지 1시간 칸으로 나뉘고, 고를 수 있는 값은 1~6시간이다.
 * 맨 왼쪽(0) 자리는 글자 없이 비워 두고, 손잡이는 1h 보다 왼쪽으로 못 간다 (끌어도 1h 에 멈춤). 기본 1시간
 * 디자인: 잉크 테두리 트랙 + 노란 손잡이 (index.css sticker-range). 고른 곳까지 핑크로 채운다 (--fill)
 */
export default function TimeSlider({ value, onChange }: TimeSliderProps) {
  const index = Math.min(MAX_INDEX, Math.max(MIN_INDEX, Math.round(value / HOUR)))
  const fill = `${(index / MAX_INDEX) * 100}%`

  return (
    <div className="px-1">
      <input
        type="range"
        min={0}
        max={MAX_INDEX}
        step={1}
        value={index}
        onChange={(e) => onChange(Math.max(MIN_INDEX, Number(e.target.value)) * HOUR)}
        aria-label="이용 시간"
        aria-valuetext={`${index}시간`}
        style={{ '--fill': fill } as CSSProperties}
        className="sticker-range w-full"
      />
      <div className="mt-2 flex justify-between text-[11px]">
        {Array.from({ length: MAX_INDEX + 1 }, (_, i) =>
          i === 0 ? (
            // 0 자리: 글자는 안 보이지만 다른 눈금과 간격을 맞추려고 자리만 차지한다
            <span key={i} aria-hidden className="invisible">
              0h
            </span>
          ) : (
            <span key={i} className={i === index ? 'font-bold text-primary-dark' : 'text-neutral-400'}>
              {i}h
            </span>
          ),
        )}
      </div>
    </div>
  )
}
