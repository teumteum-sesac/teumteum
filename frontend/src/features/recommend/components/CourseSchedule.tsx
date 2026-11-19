import { useState } from 'react'
import { TRANSPORT_STICKER } from '../../../shared/assets/stickerAssets'
import Icon, { type IconName } from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import KakaoPlaceSheet from '../../../shared/components/KakaoPlaceSheet'
import { getCategoryLabel } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { buildSchedule, getLegMode, parseTransitInfo, type Course, type CoursePlace } from '../types'
import KakaoMap from '../../map/components/KakaoMap'
import PlaceThumbnail from './PlaceThumbnail'

interface CourseScheduleProps {
  course: Course
  /** 넘기면 장소마다 '교체' 버튼 표시 (DET-01). 저장한 코스 상세(MY-03)에서는 넘기지 않는다 */
  onReplace?: (place: CoursePlace) => void
}

/**
 * 코스 일정: 출발지 → 장소(방문 순서) → 도착지 (DET-01 ②, MY-03)
 * 장소마다 방문 시각·체류시간·주소·운영시간을 보여주고, 누르면 사진·소개를 펼친다.
 * 장소를 누르면 위치 지도·사진·소개를 펼친다. 카카오맵 상세 정보는 크기 조절 가능한 하단 시트에서 본다.
 * 디자인 (목업 코스 타임라인): 왼쪽 핑크 점선 위에 출발(핀)·번호 원·도착(깃발), 구간마다 이동수단 스티커 알약
 */
export default function CourseSchedule({ course, onReplace }: CourseScheduleProps) {
  const [openPlaceId, setOpenPlaceId] = useState<string | null>(null)
  const [selectedPlaceId, setSelectedPlaceId] = useState<string | null>(null)
  const selectedPlace = course.places.find((place) => place.placeId === selectedPlaceId) ?? course.places[0]

  function togglePlace(place: CoursePlace) {
    setSelectedPlaceId(place.placeId)
    setOpenPlaceId(openPlaceId === place.placeId ? null : place.placeId)
  }

  const { rows, endTime } = buildSchedule(course)

  return (
    <>
    <ol className="relative ml-3 flex flex-col gap-3 border-l-2 border-dashed border-primary/50 pl-6">
      <Point icon="location" title={`출발 · ${course.origin.name}`} sub={course.startTime} />

      {rows.map(({ place, arrive, leave }) => {
        const open = openPlaceId === place.placeId
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
                  onClick={() => togglePlace(place)}
                  className="flex min-w-0 flex-1 gap-3 text-left"
                  aria-expanded={open}
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

              <button
                type="button"
                onClick={() => togglePlace(place)}
                className="mt-2 flex w-full items-center justify-between gap-2 text-left text-[11px] text-neutral-500"
              >
                <span className="truncate">
                  {place.address}
                  {place.openingHours && ` · ${place.openingHours}`}
                </span>
                <span className="flex shrink-0 items-center gap-0.5 font-bold text-primary-dark">
                  {open ? '접기' : '장소 보기'}
                  <Icon name="chevronDown" className={`size-3.5 transition-transform ${open ? 'rotate-180' : ''}`} />
                </span>
              </button>

              {/* 펼친 내용: 위치·사진·소개. 더 자세한 정보는 하단 시트에서 본다. */}
              {open && (
                <div className="mt-3 flex flex-col gap-2 border-t-2 border-dashed border-line pt-3 text-xs text-neutral-600">
                  {place.imageUrl && <PlaceThumbnail place={place} className="aspect-video w-full" />}
                  <div className="overflow-hidden rounded-xl border-2 border-line">
                    <KakaoMap
                      center={{ lat: place.lat, lng: place.lng }}
                      level={3}
                      markers={[{ lat: place.lat, lng: place.lng, label: String(place.visitOrder), title: place.name }]}
                      className="h-40 w-full"
                    />
                  </div>
                  {place.description ? (
                    <p className="leading-relaxed">{place.description}</p>
                  ) : (
                    <p className="text-neutral-400">사진·소개는 장소 상세 정보가 준비되면 함께 보여 드려요.</p>
                  )}
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
    {selectedPlace && (
      <KakaoPlaceSheet key={selectedPlace.placeId} kakaoPlaceId={selectedPlace.kakaoPlaceId} placeName={selectedPlace.name}>
        <div className="flex flex-col gap-4 p-4 text-sm">
          {selectedPlace.imageUrl && <PlaceThumbnail place={selectedPlace} className="aspect-video w-full" />}
          <p>{selectedPlace.address}</p>
          <p>{selectedPlace.openingHours ?? '운영시간 정보가 없어요.'}</p>
          {selectedPlace.description && <p className="whitespace-pre-line leading-relaxed">{selectedPlace.description}</p>}
          <div className="overflow-hidden rounded-xl border-2 border-line">
            <KakaoMap
              center={{ lat: selectedPlace.lat, lng: selectedPlace.lng }}
              level={3}
              markers={[{ lat: selectedPlace.lat, lng: selectedPlace.lng, title: selectedPlace.name }]}
              className="h-52 w-full"
            />
          </div>
          <p className="text-xs text-neutral-500">코스를 저장할 당시의 장소 정보예요.</p>
        </div>
      </KakaoPlaceSheet>
    )}
    </>
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
