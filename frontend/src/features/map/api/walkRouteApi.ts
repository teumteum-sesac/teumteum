import { apiGet } from '../../../shared/api/request'
import type { LatLng } from '../../../shared/types/domain'
import type { WalkRouteResponse } from '../types'

/**
 * 두 지점 사이 도보 경로 (GET /api/routes/walk). 장소 상세 지도에 내 위치 → 가게 길을 그릴 때 쓴다.
 * 서버는 추천과 같은 이동시간 서비스(OpenRouteService 도보, 실패하면 직선 추정)를 쓴다.
 * 실패하면 ApiError 를 던진다 (shared/api/request.ts).
 */
export function getWalkRoute(from: LatLng, to: LatLng): Promise<WalkRouteResponse> {
  return apiGet<WalkRouteResponse>('/routes/walk', { fromLat: from.lat, fromLng: from.lng, toLat: to.lat, toLng: to.lng })
}
