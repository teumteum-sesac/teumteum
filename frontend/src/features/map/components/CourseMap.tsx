import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import type { LatLng } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { getTotalTravelMinutes, type Course } from '../../recommend/types'
import { buildRoutePath, getRouteStops, type RouteLegResponse } from '../routePath'
import KakaoMap, { type MapMarker } from './KakaoMap'

interface CourseMapProps {
  course: Course
  /** 돌아가기 버튼 문구 */
  backLabel: string
  /** 저장 지도의 직접 진입에서도 저장 상세로 돌아갈 수 있도록 지정한다. */
  backTo?: string
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
export default function CourseMap({ course, backLabel, backTo, routeLegs }: CourseMapProps) {
  const navigate = useNavigate()

  const markers = useMemo<MapMarker[]>(() => {
    const { places, origin, destination } = course
    return [
      { lat: origin.lat, lng: origin.lng, label: 'S', tone: 'dark', title: origin.name },
      ...places.map<MapMarker>((p, i) => {
        const next = places[i + 1]?.travelMinutes ?? (destination ? course.finalTravelMinutes : null)
        return {
          lat: p.lat,
          lng: p.lng,
          label: String(p.visitOrder),
          title: `${p.name} · 체류 ${formatMinutes(p.stayMinutes)}${next !== null ? ` · 다음 이동 ${formatMinutes(next)}` : ''}`,
        }
      }),
      ...(destination ? [{ lat: destination.lat, lng: destination.lng, label: 'E', tone: 'dark' as const, title: destination.name }] : []),
    ]
  }, [course])

  // 경로선 좌표. visitOrder 번호는 출발지 0, 장소 1~N, 도착지 N+1 (RouteLegResponse 와 같은 기준)
  const routePath = useMemo(() => buildRoutePath(getRouteStops(course), routeLegs), [course, routeLegs])

  const [center, setCenter] = useState<LatLng>(markers[0])

  return (
    <>
      <KakaoMap center={center} level={5} markers={markers} polyline={routePath} fitBounds className="min-h-80 flex-1" />

      <section className="border-t border-line px-4 py-4">
        <p className="mb-3 text-sm">
          <span className="font-bold">총 {formatMinutes(course.totalMinutes)}</span>
          <span className="ml-2 text-neutral-500">이동 {formatMinutes(getTotalTravelMinutes(course))}</span>
        </p>
        <ol className="flex flex-col gap-1">
          {markers.map((m) => (
            <MapRow key={m.label} label={m.label ?? ''} name={m.title ?? ''} dark={m.tone === 'dark'} onClick={() => setCenter({ lat: m.lat, lng: m.lng })} />
          ))}
        </ol>
      </section>

      <BottomActionBar>
        <Button variant="secondary" onClick={() => { if (backTo) navigate(backTo); else navigate(-1) }}>
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
