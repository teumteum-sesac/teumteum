import { apiGet } from '../../../shared/api/request'
import type { PlaceDetailResponse } from '../types'

export interface PlaceDetailParams {
  /** 카카오 장소 ID */
  kakaoPlaceId: string
  /** 장소명 (TourAPI 에서 같은 장소를 찾는 데 쓴다) */
  name: string
  lat: number
  lng: number
}

/**
 * 장소 상세 - 카카오 장소 ID 로 (GET /api/places/detail, 정종일 #159).
 * 홈 검색·주변 탐색·추천 코스의 장소처럼 카카오 ID 와 이름·좌표를 이미 알고 있을 때 쓴다.
 * 실패하면 ApiError 를 던진다 (shared/api/request.ts).
 */
export function getPlaceDetail({ kakaoPlaceId, name, lat, lng }: PlaceDetailParams): Promise<PlaceDetailResponse> {
  return apiGet<PlaceDetailResponse>('/places/detail', { kakaoPlaceId, name, lat, lng })
}

/**
 * 장소 상세 - 우리 장소 ID 로 (GET /api/places/{placeId}/detail, 정종일 #184).
 * 저장한 코스의 placeId 는 카카오 ID 가 아니라 이 값이다. 없는 ID 면 404 PLACE_NOT_FOUND.
 */
export function getPlaceDetailById(placeId: string): Promise<PlaceDetailResponse> {
  return apiGet<PlaceDetailResponse>(`/places/${encodeURIComponent(placeId)}/detail`)
}
