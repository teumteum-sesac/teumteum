import { apiGet } from '../../../shared/api/request'
import { CURRENT_LOCATION_NAME, type LatLng, type LocationValue } from '../../../shared/types/domain'
import type { AddressResponse, PlaceSearchResult } from '../types'

/** 검색어 최대 길이 (백엔드 검증과 같음) */
export const MAX_QUERY_LENGTH = 100

/**
 * 장소·주소 검색 (CON-02 자동완성, REQ-007).
 * center 를 주면 그 좌표 기준 거리(distance)가 함께 온다.
 */
export function searchPlaces(query: string, center?: LatLng | null) {
  const q = query.trim().slice(0, MAX_QUERY_LENGTH)
  return apiGet<PlaceSearchResult[]>('/places/search', center ? { query: q, lat: center.lat, lng: center.lng } : { query: q })
}

/** 좌표→주소 변환 (현재 위치 표시용). 주소를 못 찾으면 모든 값이 null */
export function getAddress(lat: number, lng: number) {
  return apiGet<AddressResponse>('/places/address', { lat, lng })
}

/**
 * 현재 위치 좌표 → 출발지·도착지 값 (current: true). 이름은 그 자리의 건물·주소 (GET /api/places/address).
 * 주소를 못 찾거나 조회에 실패해도 좌표는 쓸 수 있으므로 이름만 '현재 위치' 로 둔다.
 * 조건 화면 첫 진입(기본값)과 위치 설정의 [현재 위치 사용] 이 같이 쓴다.
 */
export async function toCurrentLocation(lat: number, lng: number): Promise<LocationValue> {
  try {
    const result = await getAddress(lat, lng)
    const name = result.displayName ?? CURRENT_LOCATION_NAME
    const address = result.roadAddress ?? result.jibunAddress ?? undefined
    return { name, address: address !== name ? address : undefined, lat, lng, current: true }
  } catch {
    return { name: CURRENT_LOCATION_NAME, lat, lng, current: true }
  }
}

/** 검색 결과 → 출발지·도착지 값. 주소가 이름과 같으면 한 번만 보이게 뺀다 */
export function toLocationValue(result: PlaceSearchResult): LocationValue {
  const address = result.roadAddress ?? result.address ?? undefined
  return {
    name: result.name,
    address: address && address !== result.name ? address : undefined,
    lat: result.latitude,
    lng: result.longitude,
  }
}
