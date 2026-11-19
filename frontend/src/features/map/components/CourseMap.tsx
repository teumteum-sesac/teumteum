import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { useGoBack } from '../../../shared/hooks/useGoBack'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import type { LatLng } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import type { Course } from '../../recommend/types'
import { buildRoutePath, getRouteStops, type RouteLegResponse } from '../routePath'
import { coursePlaceLink } from '../coursePlaceLink'
import CourseMapSheet from './CourseMapSheet'
import KakaoMap, { type BoundsPadding, type MapMarker } from './KakaoMap'
import MapLocateButton from './MapLocateButton'
import { straightDistance } from '../format'

/** 출발지·도착지가 이 거리(m) 안이면 같은 곳으로 보고 마커를 깃발 하나만 찍는다 (둘 다 현재 위치 기본값일 때) */
const SAME_SPOT_M = 30

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
   * 하단 버튼이 갈 화면.
   * - backBy 'open'(기본): backTo 를 새로 연다 (추천 결과 → 지도 → [코스 상세 보기] 처럼 "다음 화면" 으로 갈 때)
   * - backBy 'return'  : 이전 화면으로 돌아간다. 기록이 없을 때(주소 직접 입력)만 backTo 로 간다.
   *                      (저장 상세 → 지도 → [돌아가기] 에서 상세를 새로 열면 상세·지도가 기록에 계속 쌓인다)
   * backTo 가 없으면 이전 화면으로 돌아간다.
   */
  backTo?: string
  backBy?: 'open' | 'return'
  /**
   * 지도 경로 구간 (추천 상세·저장 코스 응답의 routeLegs). ACTUAL 구간은 실제 경로 좌표를 따라 그리고,
   * STRAIGHT·구간 없음·값 없음은 지점 사이 직선으로 그린다 (REQ-041)
   */
  routeLegs?: RouteLegResponse[]
}

/**
 * 코스 지도 상세 (DET-02): 출발(S)·방문 순번·도착(E) 마커와 경로선, 전체 코스가 한 화면에 보이게 맞춤.
 * 아래에 높이가 고정된 코스 패널(CourseMapSheet)이 있다 (지점 칩 + 고른 장소 [상세보기]).
 * 번호 마커나 패널의 지점을 누르면 그 지점으로 지도를 옮기고 말풍선을 띄우며, 패널에서도 그 지점을 강조한다.
 * 장소를 고르면 패널의 [상세보기] 로 장소 상세 화면(/places/:placeId)에 간다 (코스 일정과 같은 화면).
 * 경로선: 차량·도보는 실제 길, 대중교통은 ODsay 의 지나는 정류장 좌표를 잇는다 (좌표가 없으면 직선)
 */
export default function CourseMap({ course, backLabel, backTo, backBy = 'open', routeLegs }: CourseMapProps) {
  const navigate = useNavigate()
  const goBack = useGoBack(backTo ?? '/')
  // 고른 지점 ('S', '1'…, 'E'). 지도 마커·패널이 함께 쓴다
  const [selectedId, setSelectedId] = useState<string | null>(null)
  // 지도 중심. 같은 좌표라도 새 객체를 넘기면 다시 이동한다 (KakaoMap)
  const [center, setCenter] = useState<LatLng>({ lat: course.origin.lat, lng: course.origin.lng })
  // 패널에서 고른 마커 (지도에서 강조 + 말풍선). 같은 항목을 다시 눌러도 동작하도록 매번 새 객체
  const [selectMarker, setSelectMarker] = useState<{ id: string } | null>(null)
  // 현재 위치 버튼으로 받은 위치 (지도에 현재 위치 스티커). 코스 마커·경로선과 따로 그려 범위를 다시 맞추지 않는다
  const [located, setLocated] = useState<LatLng | null>(null)

  // 출발지 = 도착지(거의 같은 자리)면 S 핀과 깃발이 겹친다 → 깃발만 찍고, 패널의 S 를 눌러도 깃발을 고른다
  const sameSpot = course.destination !== null && course.destination !== undefined && straightDistance(course.origin, course.destination) <= SAME_SPOT_M

  const markers = useMemo<MapMarker[]>(() => {
    const { places, origin, destination } = course
    // 마커를 누르면 지도를 그 지점으로 옮기고 패널에서도 고른다. 같은 마커를 다시 누르면 지도처럼 선택 해제
    const pick = (id: string, lat: number, lng: number) => () => {
      setSelectedId((prev) => (prev === id ? null : id))
      setCenter({ lat, lng })
    }
    return [
      ...(sameSpot
        ? []
        : [{ id: 'S', lat: origin.lat, lng: origin.lng, label: 'S', tone: 'dark' as const, title: origin.name, onClick: pick('S', origin.lat, origin.lng) }]),
      ...places.map<MapMarker>((p, i) => {
        const next = places[i + 1]?.travelMinutes ?? (destination ? course.finalTravelMinutes : null)
        const id = String(p.visitOrder)
        return {
          id,
          lat: p.lat,
          lng: p.lng,
          label: id,
          title: `${p.name} · 체류 ${formatMinutes(p.stayMinutes)}${next !== null ? ` · 다음 이동 ${formatMinutes(next)}` : ''}`,
          onClick: pick(id, p.lat, p.lng),
        }
      }),
      // 도착지(E)는 설정했을 때만, 지도에서는 깃발 스티커로 그린다 (목록 번호는 'E')
      ...(destination
        ? [
            {
              id: 'E',
              lat: destination.lat,
              lng: destination.lng,
              label: 'E',
              icon: 'flag' as const,
              tone: 'dark' as const,
              title: sameSpot ? `출발·도착 · ${destination.name}` : destination.name,
              onClick: pick('E', destination.lat, destination.lng),
            },
          ]
        : []),
    ]
  }, [course, sameSpot])

  // 경로선 좌표. visitOrder 번호는 출발지 0, 장소 1~N, 도착지 N+1 (RouteLegResponse 와 같은 기준)
  const routePath = useMemo(() => buildRoutePath(getRouteStops(course), routeLegs), [course, routeLegs])

  // 고른 지점이 장소면 그 장소 (출발·도착이면 null)
  const selectedPlace = course.places.find((p) => String(p.visitOrder) === selectedId) ?? null

  // 패널에서 지점을 고름: 지도를 그 지점으로 옮기고 말풍선
  const selectFromSheet = (picked: string) => {
    // 같은 자리라 S 마커가 없으면 깃발(E)을 고른다
    const id = sameSpot && picked === 'S' ? 'E' : picked
    const m = markers.find((marker) => marker.id === id)
    if (!m) return
    setSelectedId(id)
    setCenter({ lat: m.lat, lng: m.lng })
    setSelectMarker({ id })
  }

  return (
    <>
      {/* 지도 영역: 지도는 접힌 패널 위까지만 쓰고(bottom-40), 패널은 그 위에 겹쳐 올라온다. 패널을 펼쳐도 지도 크기는 그대로 */}
      <div className="relative min-h-96 flex-1">
        <div className="absolute inset-x-0 top-0 bottom-40">
          <KakaoMap
            center={center}
            level={5}
            markers={markers}
            polyline={routePath}
            fitBounds
            boundsPadding={COURSE_BOUNDS_PADDING}
            selectMarker={selectMarker}
            currentPosition={located}
            className="h-full w-full"
          />
          <MapLocateButton
            className="right-3 bottom-8"
            onLocate={(position) => {
              setLocated(position)
              setCenter({ ...position })
            }}
          />
        </div>
        <CourseMapSheet
          course={course}
          selectedId={selectedId}
          onSelect={selectFromSheet}
          // [상세보기]: 코스 일정에서 장소를 누를 때와 같은 장소 상세 화면(/places/:placeId)으로 간다
          onOpenPlace={() => {
            if (!selectedPlace) return
            const { to, state } = coursePlaceLink(selectedPlace)
            navigate(to, { state })
          }}
        />
      </div>

      <BottomActionBar>
        <Button variant="secondary" onClick={() => (backTo && backBy === 'open' ? navigate(backTo) : goBack())}>
          {backLabel}
        </Button>
      </BottomActionBar>

    </>
  )
}
