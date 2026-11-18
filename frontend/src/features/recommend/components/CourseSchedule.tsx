import { useState } from 'react'
import { TRANSPORT_STICKER } from '../../../shared/assets/stickerAssets'
import Icon, { type IconName } from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import { getCategoryLabel, getKakaoMapLink } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { buildSchedule, getLegMode, parseTransitInfo, type Course, type CoursePlace } from '../types'
import PlaceThumbnail from './PlaceThumbnail'

interface CourseScheduleProps {
  course: Course
  /** 넘기면 장소마다 '교체' 버튼 표시 (DET-01). 저장한 코스 상세(MY-03)에서는 넘기지 않는다 */
  onReplace?: (place: CoursePlace) => void
}

/**
 * 코스 일정: 출발지 → 장소(방문 순서) → 도착지 (DET-01 ②, MY-03)
 * 장소마다 방문 시각·체류시간·주소·운영시간을 보여주고, 누르면 사진·소개를 펼친다.
 * 사진·소개가 없는 장소는 '카카오맵에서 보기' 링크로 대신한다.
 * 디자인 (목업 코스 타임라인): 왼쪽 핑크 점선 위에 출발(핀)·번호 원·도착(깃발), 구간마다 이동수단 스티커 알약
 */
export default function CourseSchedule({ course, onReplace }: CourseScheduleProps) {
  const [openPlaceId, setOpenPlaceId] = useState<string | null>(null)

  const { rows, endTime } = buildSchedule(course)

  return (
    <ol className="relative ml-3 flex flex-col gap-3 border-l-2 border-dashed border-primary/50 pl-6">
      <Point icon="location" title={`출발 · ${course.origin.name}`} sub={course.startTime} />

      {rows.map(({ place, arrive, leave }) => {
        const open = openPlaceId === place.placeId
        const hasDetail = Boolean(place.imageUrl || place.description)
        return (
          <li key={place.placeId} className="relative">
            <Leg course={course} minutes={place.travelMinutes} transitInfo={place.transitInfo} />
            <span className="absolute top-[52px] -left-[39px] flex size-7 items-center justify-center rounded-full border-2 border-ink bg-primary font-display text-sm text-white">
              {place.visitOrder}
            </span>
            <div className="paper-card p-3">
              <div className="flex items-start gap-3">
                <button
                  type="button"
                  onClick={() => hasDetail && setOpenPlaceId(open ? null : place.placeId)}
                  className="flex min-w-0 flex-1 gap-3 text-left"
                  aria-expanded={hasDetail ? open : undefined}
                >
                  <PlaceThumbnail place={place} className="size-14" />
                  <span className="min-w-0 flex-1">
                    <p className="truncate font-bold">{place.name}</p>
                    <p className="text-[11px] text-neutral-500">
                      {getCategoryLabel(place.category)} › {place.subCategory}
                    </p>
                    <p className="mt-1 flex flex-wrap items-center gap-1.5 text-xs font-bold">
                      {arrive} ~ {leave}
                      <span className="rounded-full bg-sunny/60 px-1.5 py-px text-[11px]">체류 {formatMinutes(place.stayMinutes)}</span>
                    </p>
                  </span>
                </button>
                {onReplace && (
                  <button
                    type="button"
                    onClick={() => onReplace(place)}
                    className="shrink-0 rounded-full border-2 border-line bg-cream px-2.5 py-1 text-xs font-bold active:border-primary"
                  >
                    교체
                  </button>
                )}
              </div>

              <p className="mt-2 text-[11px] text-neutral-500">
                {place.address}
                {place.openingHours && ` · ${place.openingHours}`}
              </p>

              {!hasDetail && (
                <a
                  href={getKakaoMapLink(place.name, place.lat, place.lng)}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-1.5 inline-flex items-center gap-0.5 text-xs font-bold text-primary-dark"
                >
                  카카오맵에서 보기
                  <Icon name="chevronRight" className="size-3.5" />
                </a>
              )}

              {open && (
                <div className="mt-3 flex flex-col gap-2 border-t-2 border-dashed border-line pt-3 text-xs text-neutral-600">
                  <PlaceThumbnail place={place} className="aspect-video w-full" />
                  {place.description && <p>{place.description}</p>}
                </div>
              )}
            </div>
          </li>
        )
      })}

      {course.destination ? (
        <li className="relative">
          <Leg course={course} minutes={course.finalTravelMinutes} />
          <PointBody icon="flag" title={`도착 · ${course.destination.name}`} sub={endTime} />
        </li>
      ) : (
        <Point icon="flag" title="코스 종료" sub={`${endTime} · 총 ${formatMinutes(course.totalMinutes)}`} />
      )}
    </ol>
  )
}

/**
 * 구간 이동: 이동수단 스티커가 붙은 작은 알약
 * - 노선이 있으면 "지하철 · 수도권 2호선 · 15분 · 환승 0회", "버스+지하철 · 버스 3411 → 수도권 2호선 · 25분 · 환승 1회"
 * - 없으면 "도보 8분", "대중교통 12분"
 */
function Leg({ course, minutes, transitInfo }: { course: Course; minutes: number; transitInfo?: string }) {
  const mode = getLegMode(course, transitInfo)
  const { lines, transfer } = transitInfo ? parseTransitInfo(transitInfo) : { lines: [], transfer: null }
  const text =
    lines.length > 0
      ? [mode, lines.join(' → '), formatMinutes(minutes), transfer].filter(Boolean).join(' · ')
      : `${mode} ${formatMinutes(minutes)}`
  return (
    <p className="mb-2 inline-flex items-center gap-1 rounded-full bg-cream/80 py-0.5 pr-2.5 pl-1 text-[11px] text-neutral-500">
      <StickerImage name={TRANSPORT_STICKER[course.transportType]} className="size-5" />
      {text}
    </p>
  )
}

function Point({ icon, title, sub }: { icon: IconName; title: string; sub: string }) {
  return (
    <li className="relative">
      <PointBody icon={icon} title={title} sub={sub} />
    </li>
  )
}

/** 출발·도착 지점: 점선 위에 노란 원 + 아이콘 */
function PointBody({ icon, title, sub }: { icon: IconName; title: string; sub: string }) {
  return (
    <div className="relative py-0.5">
      <span className="absolute top-0 -left-[39px] flex size-7 items-center justify-center rounded-full border-2 border-ink bg-sunny">
        <Icon name={icon} className="size-4" strokeWidth={2.6} />
      </span>
      <p className="text-sm font-bold">{title}</p>
      <p className="text-xs text-neutral-500">{sub}</p>
    </div>
  )
}
