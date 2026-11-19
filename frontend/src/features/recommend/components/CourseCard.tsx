import { Fragment } from 'react'
import { Link } from 'react-router'
import Icon from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import { formatMinutes } from '../../../shared/utils/format'
import { getLegMode, getTotalTravelMinutes, type Course } from '../types'
import PlaceThumbnail from './PlaceThumbnail'

interface CourseCardProps {
  course: Course
  /** 카드 제목 앞 순번 (예: 추천 1) */
  index: number
  /** 카드를 눌렀을 때 이동할 주소 */
  to: string
}

/** 카드마다 테이프 색을 번갈아 붙여 스크랩북 느낌을 낸다 */
const TAPES = ['decoTapePink', 'decoTapeYellow'] as const

/**
 * 추천 코스 카드: 썸네일, 장소 수, 총 소요시간, 방문 순서, 장소 간 이동시간 (RES-01 ③)
 * 디자인 (목업 RES-01):
 *   위 = 연한 핑크 띠에 총 소요시간(크게) + 이동·체류 합계
 *   아래 = 번호 원 + 썸네일 + 장소명·세분류·체류 칩이 세로로 이어지는 미니 타임라인, 사이에 이동 구간
 *   모서리에 마스킹테이프 장식
 */
export default function CourseCard({ course, index, to }: CourseCardProps) {
  const travel = getTotalTravelMinutes(course)
  const stay = course.places.reduce((sum, p) => sum + p.stayMinutes, 0)

  return (
    <Link to={to} className="paper-card relative block overflow-visible active:translate-y-0.5">
      <StickerImage
        name={TAPES[(index - 1) % TAPES.length]}
        className="pointer-events-none absolute -top-4 right-4 z-10 w-16"
        ratio="aspect-[3/2]"
      />

      <div className="flex items-center gap-3 rounded-t-[14px] bg-primary-light/70 px-4 py-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full border-2 border-ink bg-cream text-primary-dark">
          <Icon name="clock" className="size-5" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-x-1.5 font-display text-[17px] leading-tight">
            추천 {index} · {formatMinutes(course.totalMinutes)} 코스
            {course.estimated && (
              <span className="rounded-full bg-sunny px-2 py-0.5 font-sans text-[11px] font-bold">예상 시간</span>
            )}
          </p>
          <p className="mt-0.5 text-xs text-neutral-600">
            {course.places.length}곳 · 이동 {formatMinutes(travel)} · 체류 {formatMinutes(stay)}
          </p>
        </div>
        <Icon name="chevronRight" className="size-5 text-neutral-500" />
      </div>

      <ol className="flex flex-col px-4 py-3">
        {course.places.map((place, i) => (
          <Fragment key={place.placeId}>
            {i > 0 && (
              // 이동 구간: 번호 원 아래로 이어지는 점선 + 이동 수단·시간
              <li aria-hidden className="ml-[11px] flex items-center gap-2 border-l-2 border-dashed border-primary/60 py-1 pl-5 text-[11px] text-neutral-500">
                {getLegMode(course, place.transitInfo)} {formatMinutes(place.travelMinutes)}
              </li>
            )}
            <li className="flex items-center gap-3">
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary font-display text-[13px] text-white">
                {i + 1}
              </span>
              <PlaceThumbnail place={place} className="size-12" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold">{place.name}</p>
                <p className="mt-0.5 flex items-center gap-1.5 text-[11px] text-neutral-500">
                  {place.subCategory}
                  <span className="rounded-full bg-sunny/60 px-1.5 py-px font-bold text-ink">체류 {formatMinutes(place.stayMinutes)}</span>
                </p>
              </div>
            </li>
          </Fragment>
        ))}
      </ol>
    </Link>
  )
}
