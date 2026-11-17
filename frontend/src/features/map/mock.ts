import type { LatLng } from '../../shared/types/domain'
import type { RouteLegResponse, RouteStop } from './routePath'

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
