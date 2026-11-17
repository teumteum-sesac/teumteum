import type { ReactNode } from 'react'
import Icon, { type IconName } from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import { getTransportLabel } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { getCourseName, getTotalTravelMinutes, type Course } from '../types'

interface CourseSummaryProps {
  course: Course
  /** 이동수단 대신 보여줄 문구 (MY-03: "11.11 저장") */
  meta?: string
  /** 요약 아래 추가 줄 (MY-03: 저장 시점 정보 안내) */
  children?: ReactNode
}

/**
 * 코스 요약: 코스명, 총 예상 소요시간, 장소 수, 이동수단 (DET-01 ①, MY-03 ①)
 * 디자인 (목업 코스 상세): 듀오 장면 배너 → 그 아래로 겹쳐 붙인 초록 코스명 라벨 → 시간 칩 줄
 */
export default function CourseSummary({ course, meta, children }: CourseSummaryProps) {
  const travel = getTotalTravelMinutes(course)
  const stay = course.places.reduce((sum, p) => sum + p.stayMinutes, 0)

  return (
    <section className="px-4 pt-2 pb-4">
      <StickerImage name="sceneCourse" className="mx-auto w-full max-w-80" ratio="aspect-[3/2]" />

      {/* 배너 아래쪽에 살짝 겹쳐 붙인 라벨 (스티커 위에 이름표를 붙인 느낌) */}
      <h2 className="relative mx-auto -mt-7 w-fit max-w-full -rotate-1 rounded-2xl border-2 border-ink bg-turtle px-4 py-1.5 text-center font-display text-lg leading-snug shadow-[0_3px_0_var(--color-ink)]">
        {getCourseName(course)}
      </h2>

      <div className="mt-4 flex flex-wrap justify-center gap-1.5">
        <Pill icon="clock" strong>
          총 {formatMinutes(course.totalMinutes)}
        </Pill>
        <Pill icon="location">{course.places.length}곳</Pill>
        <Pill icon="map">이동 {formatMinutes(travel)}</Pill>
        <Pill icon="bookmark">체류 {formatMinutes(stay)}</Pill>
        <Pill>{meta ?? getTransportLabel(course.transportType)}</Pill>
        {course.estimated && <span className="rounded-full bg-sunny px-2.5 py-1 text-xs font-bold">예상 시간</span>}
      </div>
      {children}
    </section>
  )
}

/** 요약 칩: 크림 바탕 + 종이색 테두리. strong 이면 노란 바탕으로 강조 (총 소요시간) */
function Pill({ icon, strong = false, children }: { icon?: IconName; strong?: boolean; children: ReactNode }) {
  return (
    <span
      className={`flex items-center gap-1 rounded-full border-2 px-2.5 py-1 text-xs font-bold ${
        strong ? 'border-ink bg-sunny' : 'border-line bg-cream text-neutral-700'
      }`}
    >
      {icon && <Icon name={icon} className="size-3.5" strokeWidth={2.6} />}
      {children}
    </span>
  )
}
