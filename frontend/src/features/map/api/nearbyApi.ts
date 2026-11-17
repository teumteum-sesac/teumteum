import { apiGet } from '../../../shared/api/request'
import type { NearbyPlaceResult } from '../types'

export interface NearbyPlacesParams {
  lat: number
  lng: number
  /** 검색 반경(m). HOM-02 는 500 / 1000 / 3000 */
  radius: number
}

/**
 * 주변 장소 목록 (GET /api/places/nearby, 정종일 #81).
 * categories 는 보내지 않는다 → 대분류 8개 전체를 받고, 카테고리 칩은 화면에서 거른다.
 * 실패하면 ApiError 를 던진다 (shared/api/request.ts).
 */
export function getNearbyPlaces({ lat, lng, radius }: NearbyPlacesParams): Promise<NearbyPlaceResult[]> {
  return apiGet<NearbyPlaceResult[]>('/places/nearby', { lat, lng, radius })
}
