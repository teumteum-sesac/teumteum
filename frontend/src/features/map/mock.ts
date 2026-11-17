import type { CategoryCode, LatLng } from '../../shared/types/domain'
import type { RouteLegResponse, RouteStop } from './routePath'

export interface NearbyPlace {
  placeId: string
  name: string
  category: CategoryCode
  address: string
  /** 현재 위치에서 거리(m) */
  distance: number
  /** 현재 위치 기준 위치 차이 (목업 마커 표시용) */
  offset: LatLng
}

/**
 * [임시] 주변 장소·홈 검색 목업. placeId는 recommend/mock 의 장소와 같아서
 * 누르면 장소 1곳짜리 상세(/results/place-<placeId>)로 이어진다. (API 연결 전)
 * TODO(정종일): 카카오 로컬 검색·주변 장소 API 연동 후 삭제
 */
export const MOCK_NEARBY: NearbyPlace[] = [
  { placeId: 'p-2', name: '카페 B', category: 'CAFE', address: '서울 성동구 ○○길 5', distance: 320, offset: { lat: 0.0021, lng: 0.0012 } },
  { placeId: 'p-5', name: '소품샵 E', category: 'SHOPPING', address: '서울 성동구 ○○길 18', distance: 540, offset: { lat: -0.0032, lng: 0.0024 } },
  { placeId: 'p-1', name: '전시관 A', category: 'CULTURE', address: '서울 성동구 ○○로 12', distance: 780, offset: { lat: 0.0043, lng: -0.0035 } },
  { placeId: 'p-6', name: '서울숲', category: 'NATURE', address: '서울 성동구 뚝섬로 273', distance: 950, offset: { lat: -0.0061, lng: -0.0018 } },
  { placeId: 'p-4', name: '당구장 D', category: 'SPORTS', address: '서울 성동구 ○○로 44', distance: 1800, offset: { lat: 0.0102, lng: 0.0091 } },
]

/**
 * [임시] 코스 지도 경로선(REQ-041) 확인용 routeLegs 목업. 개발 서버에서 `/results/<courseId>/map?mockRoute` 로만 켠다.
 * 실제 길찾기 결과가 아니라, 구간별 처리를 눈으로 확인하려고 만든 모양이다:
 * - 1번째 구간: ACTUAL, 계단 모양으로 꺾이는 좌표 (실제 경로처럼 꺾여 그려지는지)
 * - 2번째 구간: STRAIGHT (직선 유지)
 * - 3번째 구간: ACTUAL 이지만 좌표가 잘못됨 (직선으로 대체되는지)
 * - 그 뒤 구간: 없음 (직선으로 대체되는지)
 * TODO(유지수): DET-02 API 연결(김용현)로 실제 routeLegs 가 들어오면 삭제
 */
export function mockRouteLegs(stops: RouteStop[]): RouteLegResponse[] {
  const toCoordinate = (p: LatLng) => ({ latitude: p.lat, longitude: p.lng })
  const legs: RouteLegResponse[] = []
  stops.slice(0, 3).forEach((from, i) => {
    const to = stops[i + 1]
    if (!to) return
    const base = { fromVisitOrder: from.visitOrder, toVisitOrder: to.visitOrder }
    if (i === 0) {
      const midLat = (from.lat + to.lat) / 2
      const midLng = (from.lng + to.lng) / 2
      const steps: LatLng[] = [from, { lat: from.lat, lng: midLng }, { lat: midLat, lng: midLng }, { lat: midLat, lng: to.lng }, to]
      legs.push({ ...base, pathType: 'ACTUAL', coordinates: steps.map(toCoordinate) })
    } else if (i === 1) {
      legs.push({ ...base, pathType: 'STRAIGHT', coordinates: [from, to].map(toCoordinate) })
    } else {
      legs.push({ ...base, pathType: 'ACTUAL', coordinates: [{ latitude: Number.NaN, longitude: 200 }] })
    }
  })
  return legs
}
