import { apiGet } from '../../../shared/api/request'
import type { PlaceDetailResponse } from '../types'

export function getPlaceDetail(kakaoPlaceId: string, name: string, lat: number, lng: number) {
  return apiGet<PlaceDetailResponse>('/places/detail', { kakaoPlaceId, name, lat, lng })
}
