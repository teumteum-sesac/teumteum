// 카카오 지도 JS SDK 중 우리가 쓰는 부분만 최소한으로 타입 정의한다. 필요한 API가 생기면 여기에 추가.

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
  /** 마커 클릭, 지도 이동·확대가 끝남(idle) 등 지도 객체 이벤트 */
  event: {
    addListener(target: KakaoOverlay, type: 'click', handler: () => void): void
    addListener(target: KakaoMapInstance, type: 'idle', handler: () => void): void
    removeListener(target: KakaoMapInstance, type: 'idle', handler: () => void): void
  }
}

declare global {
  interface Window {
    kakao?: { maps: KakaoMapsApi }
  }
}

// ── 주소 조회 API 응답 (CON-02 지도 중앙 핀) ──

/**
 * 좌표 → 주소. 백엔드 place/dto/AddressResponse (GET /api/places/address) 와 이름·필드를 똑같이 맞춘다.
 * 주소를 찾지 못한 좌표면 세 값이 모두 null 이다.
 */
export interface AddressResponse {
  /** 표시용 이름 (건물명 > 도로명 주소 > 지번 주소 순) */
  displayName: string | null
  roadAddress: string | null
  jibunAddress: string | null
}
