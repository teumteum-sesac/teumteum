import Icon from '../../../shared/components/Icon'
import { addMinutesToTime } from '../../../shared/utils/format'
import { useConditionStore } from '../store'
import TimeSlider from './TimeSlider'

/** 시작 시각 선택지: 30분 단위 */
const START_TIMES = Array.from({ length: 48 }, (_, i) => addMinutesToTime('00:00', i * 30))

/**
 * 시간 (CON-01 ②): 시작 시각(기본 지금, 30분 단위) + 이용 시간 슬라이더.
 * 종료 시각은 시작 + 이용 시간으로 자동 계산되어 표시만 된다.
 * 0분은 슬라이더 시작 위치이며, 0분이면 추천받기 버튼이 비활성되고 안내 문구가 뜬다 (REQ-008, 문구는 ConditionForm)
 * 디자인: 시작(고르는 칸) → 종료(자동, 노란 라벨) 를 한 장의 종이 카드에 담는다
 */
export default function TimeSelector() {
  const { startTime, availableMinutes, setStartTime, setAvailableMinutes } = useConditionStore()
  // const noLimit = availableMinutes === 0

  return (
    <div className="paper-card flex flex-col gap-4 p-3.5">
      <div className="flex items-center gap-2">
        <Icon name="clock" className="size-5 text-primary-dark" />
        <select
          value={startTime}
          onChange={(e) => setStartTime(e.target.value)}
          aria-label="시작 시각"
          className="h-10 flex-1 cursor-pointer rounded-full border-2 border-line bg-cream px-3 text-center text-sm font-bold outline-none focus:border-primary"
        >
          {START_TIMES.map((t) => (
            <option key={t} value={t}>
              {t} 시작
            </option>
          ))}
        </select>
        <Icon name="chevronRight" className="size-4 text-neutral-400" />
        <div
          aria-label="종료 시각"
          className="flex h-10 flex-1 items-center justify-center rounded-full bg-sunny/60 text-sm font-bold"
        >
          {`${addMinutesToTime(startTime, availableMinutes)} 종료`}
        </div>
      </div>
      <TimeSlider value={availableMinutes} onChange={setAvailableMinutes} />
    </div>
  )
}
