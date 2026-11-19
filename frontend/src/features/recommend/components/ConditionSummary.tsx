import { getCategoryLabel, getTransportLabel } from '../../../shared/types/domain'
import { formatTimeRange } from '../../../shared/utils/format'
import { isRandomCondition, useConditionStore } from '../../condition/store'
import { useRecommendStore } from '../store'

/**
 * 조건 요약 칩: 위치 · 시간 · 이동수단 · 카테고리 (RES-01 ①). 랜덤이면 '랜덤 추천' 칩
 * 디자인: 종이 라벨 스티커처럼 크림 바탕 + 종이색 테두리
 */
export default function ConditionSummary() {
  // 서버가 실제로 적용한 조건(result.condition)을 우선 보여 준다. 새로고침으로 외출 조건 입력값이 초기화돼도 맞게 보인다
  const applied = useRecommendStore((s) => s.result?.condition)
  const input = useConditionStore()
  const condition = applied
    ? {
        origin: { name: applied.origin.name ?? '출발지' },
        destination: applied.destination ? { name: applied.destination.name ?? '도착지' } : null,
        startTime: applied.startTime,
        availableMinutes: applied.availableMinutes,
        transportType: applied.transportType,
        categoryMode: applied.categoryMode,
        categories: applied.categories,
      }
    : input
  const { origin, destination, startTime, availableMinutes, transportType, categories } = condition

  const labels = [
    destination ? `${origin?.name ?? '출발지 미정'} → ${destination.name}` : `${origin?.name ?? '출발지 미정'} 출발`,
    formatTimeRange(startTime, availableMinutes),
    getTransportLabel(transportType),
    isRandomCondition(condition) ? '랜덤 추천' : categories.map(getCategoryLabel).join(' + '),
  ]

  return (
    <div className="flex flex-wrap gap-1.5">
      {labels.map((label) => (
        <span key={label} className="rounded-lg border-2 border-line bg-cream px-2 py-0.5 text-xs font-bold text-neutral-600">
          {label}
        </span>
      ))}
    </div>
  )
}
