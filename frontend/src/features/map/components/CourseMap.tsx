import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import type { LatLng } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { getTotalTravelMinutes, type Course } from '../../recommend/types'
import { buildRoutePath, getRouteStops, type RouteLegResponse } from '../routePath'
import KakaoMap, { type BoundsPadding, type MapMarker } from './KakaoMap'

/**
 * 코스 전체를 맞출 때 지도 가장자리에서 비울 여백(px). 가장자리 마커가 지도 밖으로 걸치지 않고,
 * 핀(좌표 위로 약 42px)과 말풍선 자리가 남도록 위쪽을 더 비운다. 바깥에 두어 렌더마다 같은 객체를 쓴다
 */
const COURSE_BOUNDS_PADDING: BoundsPadding = { top: 64, right: 36, bottom: 24, left: 36 }

interface CourseMapProps {
  course: Course
  /** 돌아가기 버튼 문구 */
  backLabel: string
  /**
   * 지도 경로 구간 (추천 상세·저장 코스 응답의 routeLegs). ACTUAL 구간은 실제 경로 좌표를 따라 그리고,
   * STRAIGHT·구간 없음·값 없음은 지점 사이 직선으로 그린다 (REQ-041)
   */
  routeLegs?: RouteLegResponse[]
}

/**
 * 코스 지도 상세 (DET-02): 출발(S)·방문 순번·도착(E) 마커와 경로선, 전체 코스가 한 화면에 보이게 맞춤.
 * 번호를 누르면 장소명·체류시간·다음 이동시간 말풍선. 차량·도보는 실제 경로, 대중교통은 1차 버전에서 장소 사이 직선
 */
export default function CourseMap({ course, backLabel, routeLegs }: CourseMapProps) {
  const navigate = useNavigate()

  const markers = useMemo<MapMarker[]>(() => {
    const { places, origin, destination } = course
    return [
      { id: 'S', lat: origin.lat, lng: origin.lng, label: 'S', tone: 'dark', title: origin.name },
      ...places.map<MapMarker>((p, i) => {
        const next = places[i + 1]?.travelMinutes ?? (destination ? course.finalTravelMinutes : null)
        return {
          id: String(p.visitOrder),
          lat: p.lat,
          lng: p.lng,
          label: String(p.visitOrder),
          title: `${p.name} · 체류 ${formatMinutes(p.stayMinutes)}${next !== null ? ` · 다음 이동 ${formatMinutes(next)}` : ''}`,
        }
      }),
      // 도착지(E)는 설정했을 때만, 지도에서는 깃발 스티커로 그린다 (목록 번호는 'E')
      ...(destination
        ? [{ id: 'E', lat: destination.lat, lng: destination.lng, label: 'E', icon: 'flag' as const, tone: 'dark' as const, title: destination.name }]
        : []),
    ]
  }, [course])

  // 경로선 좌표. visitOrder 번호는 출발지 0, 장소 1~N, 도착지 N+1 (RouteLegResponse 와 같은 기준)
  const routePath = useMemo(() => buildRoutePath(getRouteStops(course), routeLegs), [course, routeLegs])

  const [center, setCenter] = useState<LatLng>(markers[0])
  // 목록에서 고른 마커 (지도에서 강조 + 말풍선). 같은 항목을 다시 눌러도 동작하도록 매번 새 객체
  const [selectMarker, setSelectMarker] = useState<{ id: string } | null>(null)

  return (
    <>
      <KakaoMap
        center={center}
        level={5}
        markers={markers}
        polyline={routePath}
        fitBounds
        boundsPadding={COURSE_BOUNDS_PADDING}
        selectMarker={selectMarker}
        className="min-h-80 flex-1"
      />

      <section className="border-t border-line px-4 py-4">
        <p className="mb-3 text-sm">
          <span className="font-bold">총 {formatMinutes(course.totalMinutes)}</span>
          <span className="ml-2 text-neutral-500">이동 {formatMinutes(getTotalTravelMinutes(course))}</span>
        </p>
        <ol className="flex flex-col gap-1">
          {markers.map((m) => (
            <MapRow
              key={m.label}
              label={m.label ?? ''}
              name={m.title ?? ''}
              dark={m.tone === 'dark'}
              onClick={() => {
                setCenter({ lat: m.lat, lng: m.lng })
                if (m.id) setSelectMarker({ id: m.id })
              }}
            />
          ))}
        </ol>
      </section>

      <BottomActionBar>
        <Button variant="secondary" onClick={() => navigate(-1)}>
          {backLabel}
        </Button>
      </BottomActionBar>
    </>
  )
}

function MapRow({ label, name, dark, onClick }: { label: string; name: string; dark: boolean; onClick: () => void }) {
  return (
    <li>
      <button type="button" onClick={onClick} className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left active:bg-neutral-100">
        <span
          className={`flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white ${dark ? 'bg-neutral-700' : 'bg-primary'}`}
        >
          {label}
        </span>
        <span className="flex-1 truncate text-sm">{name}</span>
      </button>
    </li>
  )
}
