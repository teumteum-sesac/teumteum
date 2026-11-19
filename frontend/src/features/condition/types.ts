/*
 * 외출 조건(condition) feature 의 API 요청·응답 타입.
 * 이름은 백엔드 DTO 와 맞춘다 (AGENTS.md 6장).
 */

/** 장소·주소 검색 결과 한 줄. 백엔드 place/dto/PlaceSearchResult (GET /api/places/search) */
export interface PlaceSearchResult {
  /** PLACE(장소) 또는 ADDRESS(주소) */
  type: 'PLACE' | 'ADDRESS'
  /** 카카오 장소 ID. 주소 결과는 null */
  kakaoPlaceId: string | null
  /** 표시 이름 (장소명 또는 주소) */
  name: string
  /** 지번 주소 */
  address: string | null
  /** 도로명 주소. 없으면 null */
  roadAddress: string | null
  /** 카카오 카테고리 그룹 코드 (CE7 등). 없으면 null */
  categoryGroupCode: string | null
  /** 카카오 카테고리 전체 이름. 없으면 null */
  categoryName: string | null
  latitude: number
  longitude: number
  /** 검색 기준 좌표에서의 거리(m). 기준 좌표가 없거나 주소 결과면 null */
  distance: number | null
}

/** 좌표→주소 변환 결과. 백엔드 place/dto/AddressResponse (GET /api/places/address). 못 찾으면 모두 null */
export interface AddressResponse {
  /** 표시용 이름 (건물명 > 도로명 주소 > 지번 주소 순) */
  displayName: string | null
  roadAddress: string | null
  jibunAddress: string | null
}
