import { apiGet } from '../../../shared/api/request'
import type { LatLng } from '../../../shared/types/domain'
import type { AddressResponse } from '../types'

/**
 * 좌표 → 주소 (GET /api/places/address, 정종일 #63). 지도 중앙 핀이 가리키는 위치를 주소로 보여 줄 때 쓴다.
 * 실패하면 ApiError 를 던진다 (shared/api/request.ts).
 */
export function getAddress({ lat, lng }: LatLng): Promise<AddressResponse> {
  return apiGet<AddressResponse>('/places/address', { lat, lng })
}
