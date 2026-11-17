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
  /** 지도 중심을 화면 픽셀만큼 부드럽게 옮긴다 (dx > 0 이면 오른쪽, dy > 0 이면 아래쪽을 더 보여 줌) */
  panBy(dx: number, dy: number): void
  setLevel(level: number): void
  /** padding(px)을 주면 지도 가장자리에서 그만큼 안쪽에 마커가 오도록 맞춘다 */
  setBounds(bounds: KakaoLatLngBounds, paddingTop?: number, paddingRight?: number, paddingBottom?: number, paddingLeft?: number): void
  relayout(): void
}

/** 지도 위에 올리는 것(마커·오버레이·선)의 공통 부분 */
export interface KakaoOverlay {
  setMap(map: KakaoMapInstance | null): void
}

/** 사용자 정의 오버레이 (DOM 을 지도 위에 올림). 겹칠 때 위·아래 순서를 바꿀 수 있다 */
export interface KakaoCustomOverlay extends KakaoOverlay {
  setZIndex(zIndex: number): void
}

/** 마커 이미지 크기(px) */
export type KakaoSize = object
/** 이미지 안의 픽셀 위치 (마커 이미지의 기준점) */
export type KakaoPoint = object
/** 마커 이미지. 여러 마커가 같은 객체를 함께 써도 된다 */
export type KakaoMarkerImage = object

export interface KakaoMapsApi {
  load(callback: () => void): void
  LatLng: new (lat: number, lng: number) => KakaoLatLng
  LatLngBounds: new () => KakaoLatLngBounds
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMapInstance
  Marker: new (options: { position: KakaoLatLng; map?: KakaoMapInstance; image?: KakaoMarkerImage }) => KakaoOverlay
  Size: new (width: number, height: number) => KakaoSize
  Point: new (x: number, y: number) => KakaoPoint
  /** offset: 이미지 안에서 마커 좌표에 맞출 픽셀 위치 (예: 핀 끝) */
  MarkerImage: new (src: string, size: KakaoSize, options?: { offset?: KakaoPoint; alt?: string }) => KakaoMarkerImage
  CustomOverlay: new (options: {
    position: KakaoLatLng
    content: HTMLElement | string
    map?: KakaoMapInstance
    /** 내용 상자 안에서 좌표에 맞출 위치 (0 왼쪽 ~ 1 오른쪽, 기본 0.5) */
    xAnchor?: number
    /** 내용 상자 안에서 좌표에 맞출 위치 (0 위 ~ 1 아래, 기본 0.5) */
    yAnchor?: number
    /** 겹칠 때 위에 올 순서 (클수록 위) */
    zIndex?: number
  }) => KakaoCustomOverlay
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
    addListener(target: KakaoMapInstance, type: 'idle' | 'click', handler: () => void): void
    removeListener(target: KakaoMapInstance, type: 'idle' | 'click', handler: () => void): void
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
