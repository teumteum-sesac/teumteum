import type { CategoryCode } from '../../shared/types/domain'

// ── 주변 장소 API 응답 (HOM-02) ──

/**
 * 주변 장소 1곳. 백엔드 place/dto/NearbyPlaceResult (GET /api/places/nearby) 와 이름·필드를 똑같이 맞춘다.
 * 서버가 거리 가까운 순으로 보낸다.
 */
export interface NearbyPlaceResult {
  /** 카카오 장소 ID */
  kakaoPlaceId: string
  name: string
  /** 대분류 코드 */
  category: CategoryCode
  /** 세분류 이름 (예: '카페', '공원') */
  subCategory: string
  /** 지번 주소 */
  address: string
  /** 도로명 주소. 없으면 null */
  roadAddress: string | null
  latitude: number
  longitude: number
  /** 검색 위치에서의 거리(m). 서버가 모르면 null */
  distance: number | null
  /** 카카오맵 장소 링크 */
  placeUrl: string
}

// ── 카카오 지도 JS SDK ──
// 우리가 쓰는 부분만 최소한으로 타입 정의한다. 필요한 API가 생기면 여기에 추가.

export interface KakaoLatLng {
  getLat(): number
  getLng(): number
}

export interface KakaoLatLngBounds {
  extend(latlng: KakaoLatLng): void
}

export interface KakaoMapInstance {
  getCenter(): KakaoLatLng
  setCenter(latlng: KakaoLatLng): void
  panTo(latlng: KakaoLatLng): void
  setLevel(level: number): void
  /** padding(px)을 주면 지도 가장자리에서 그만큼 안쪽에 마커가 오도록 맞춘다 */
  setBounds(bounds: KakaoLatLngBounds, paddingTop?: number, paddingRight?: number, paddingBottom?: number, paddingLeft?: number): void
  relayout(): void
}

/** 지도 위에 올리는 것(마커·오버레이·선)의 공통 부분 */
export interface KakaoOverlay {
  setMap(map: KakaoMapInstance | null): void
}

export interface KakaoMapsApi {
  load(callback: () => void): void
  LatLng: new (lat: number, lng: number) => KakaoLatLng
  LatLngBounds: new () => KakaoLatLngBounds
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMapInstance
  Marker: new (options: { position: KakaoLatLng; map?: KakaoMapInstance }) => KakaoOverlay
  CustomOverlay: new (options: {
    position: KakaoLatLng
    content: HTMLElement | string
    map?: KakaoMapInstance
    yAnchor?: number
  }) => KakaoOverlay
  Polyline: new (options: {
    path: KakaoLatLng[]
    strokeWeight?: number
    strokeColor?: string
    strokeOpacity?: number
    map?: KakaoMapInstance
  }) => KakaoOverlay
  /** 마커 클릭 등 지도 객체 이벤트 */
  event: {
    addListener(target: KakaoOverlay, type: 'click', handler: () => void): void
  }
}

declare global {
  interface Window {
    kakao?: { maps: KakaoMapsApi }
  }
}
