import type { LatLng } from '../../shared/types/domain'
import type { Course } from '../recommend/types'

/**
 * 코스 지도(DET-02) 경로선 (REQ-041).
 * 백엔드 `RouteLegResponse`(추천 상세 · recommend-api.md) / `CourseRouteLegResponse`(저장 코스 · course-api.md 3.2) 와 같은 모양.
 * TODO(유지수·나혜빈·김용현): DET-02 API 연결 시 recommend 쪽 응답 타입과 하나로 맞추고 이 정의를 옮기거나 재사용한다
 */
export type PathType = 'ACTUAL' | 'STRAIGHT'

export interface CoordinateResponse {
  latitude: number
  longitude: number
}

/** 지도 경로 한 구간. visitOrder 번호: 출발지 0, 장소 1~N, 도착지가 있으면 N+1 */
export interface RouteLegResponse {
  fromVisitOrder: number
  toVisitOrder: number
  /** ACTUAL: 실제 경로(차량·도보) / STRAIGHT: 직선(대중교통 1차 버전, 이동시간 API 실패) */
  pathType: PathType
  /** 진행 방향 순서의 좌표 (2개 이상) */
  coordinates: CoordinateResponse[]
}

/** 경로가 지나는 지점 (출발지·장소·도착지) */
export interface RouteStop extends LatLng {
  visitOrder: number
}

/** 코스의 경로 지점: 출발지 0, 장소 visitOrder(1~N), 도착지가 있으면 N+1 */
export function getRouteStops(course: Course): RouteStop[] {
  const { places, origin, destination } = course
  return [
    { lat: origin.lat, lng: origin.lng, visitOrder: 0 },
    ...places.map((p) => ({ lat: p.lat, lng: p.lng, visitOrder: p.visitOrder })),
    ...(destination ? [{ lat: destination.lat, lng: destination.lng, visitOrder: places.length + 1 }] : []),
  ]
}

function isValidCoordinate(c: CoordinateResponse): boolean {
  return (
    Number.isFinite(c.latitude) &&
    Number.isFinite(c.longitude) &&
    Math.abs(c.latitude) <= 90 &&
    Math.abs(c.longitude) <= 180
  )
}

function samePoint(a: LatLng, b: LatLng): boolean {
  return a.lat === b.lat && a.lng === b.lng
}

/**
 * 지점 순서대로 이어지는 경로선 좌표를 만든다.
 * - 구간이 `ACTUAL` 이고 유효한 좌표가 2개 이상이면 그 좌표를 따라 그린다.
 *   길찾기 좌표는 도로 위로 맞춰져 마커와 조금 떨어질 수 있어, 구간 앞뒤에 마커 좌표를 붙여 선이 마커에 닿게 한다
 * - `STRAIGHT`, 구간 없음, 좌표 부족·오류는 두 지점을 직선으로 잇는다 (기존 표시와 같음)
 * - 이어지는 같은 좌표는 한 번만 넣는다
 */
export function buildRoutePath(stops: RouteStop[], legs: RouteLegResponse[] = []): LatLng[] {
  const path: LatLng[] = []
  const push = (p: LatLng) => {
    const last = path[path.length - 1]
    if (!last || !samePoint(last, p)) path.push({ lat: p.lat, lng: p.lng })
  }

  stops.forEach((from, i) => {
    const to = stops[i + 1]
    push(from)
    if (!to) return
    const leg = legs.find((l) => l.fromVisitOrder === from.visitOrder && l.toVisitOrder === to.visitOrder)
    if (leg?.pathType === 'ACTUAL') {
      const coords = leg.coordinates.filter(isValidCoordinate)
      if (coords.length >= 2) coords.forEach((c) => push({ lat: c.latitude, lng: c.longitude }))
    }
    // 다음 지점(to)은 다음 반복의 push(from) 에서 들어간다
  })
  return path
}
