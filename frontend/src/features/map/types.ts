import type { CategoryCode } from '../../shared/types/domain'

/** GET /api/places/detail 응답. */
export interface PlaceDetailResponse {
  kakaoPlaceId: string
  tourInfoAvailable: boolean
  imageUrl: string | null
  imageCopyrightType: string | null
  overview: string | null
  openingHours: string | null
  restDate: string | null
  kakaoMapUrl: string
}

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
  /** 남서쪽 모서리 */
  getSouthWest(): KakaoLatLng
  /** 북동쪽 모서리 */
  getNorthEast(): KakaoLatLng
}

export interface KakaoMapInstance {
  getCenter(): KakaoLatLng
  setCenter(latlng: KakaoLatLng): void
  panTo(latlng: KakaoLatLng): void
  /** 지도 중심을 화면 픽셀만큼 부드럽게 옮긴다 (dx > 0 이면 오른쪽, dy > 0 이면 아래쪽을 더 보여 줌) */
  panBy(dx: number, dy: number): void
  getLevel(): number
  /** animate: true 면 확대·축소를 애니메이션으로 한다 (기준은 지도 중심) */
  setLevel(level: number, options?: { animate?: boolean }): void
  /** 확대할 수 있는 가장 작은 레벨 (SDK 버전에 따라 없을 수 있어 선택) */
  setMinLevel?(level: number): void
  /** 축소할 수 있는 가장 큰 레벨 (SDK 버전에 따라 없을 수 있어 선택) */
  setMaxLevel?(level: number): void
  /** 지금 지도에 보이는 범위 */
  getBounds(): KakaoLatLngBounds
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
  /** 오버레이에 넣은 내용 (클러스터 숫자 원은 div) */
  getContent(): HTMLElement | string
}

/** 카카오 기본 마커 (이미지 마커). 클러스터러에 넣거나 투명도·겹침 순서를 바꿀 수 있다 */
export interface KakaoMarker extends KakaoOverlay {
  /** 0(투명) ~ 1 */
  setOpacity(opacity: number): void
  setZIndex(zIndex: number): void
}

/** 원 (탐색 반경 표시) */
export interface KakaoCircle extends KakaoOverlay {
  setPosition(latlng: KakaoLatLng): void
  /** 반지름(m) */
  setRadius(radius: number): void
}

/** 클러스터 하나 (숫자 원). 숫자 원은 CustomOverlay 라 겹침 순서를 바꿀 수 있다 */
export interface KakaoCluster {
  getClusterMarker(): KakaoCustomOverlay
}

/** 마커 클러스터러 (SDK libraries=clusterer). 가까운 마커를 숫자 원으로 묶고 확대하면 풀어 준다 */
export interface KakaoMarkerClusterer {
  addMarkers(markers: KakaoMarker[], nodraw?: boolean): void
  removeMarkers(markers: KakaoMarker[], nodraw?: boolean): void
  clear(): void
  redraw(): void
  setMap(map: KakaoMapInstance | null): void
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
  Marker: new (options: { position: KakaoLatLng; map?: KakaoMapInstance; image?: KakaoMarkerImage; zIndex?: number }) => KakaoMarker
  /** 원. fillOpacity 는 0~1 */
  Circle: new (options: {
    center: KakaoLatLng
    radius: number
    strokeWeight?: number
    strokeColor?: string
    strokeOpacity?: number
    fillColor?: string
    fillOpacity?: number
    zIndex?: number
    map?: KakaoMapInstance
  }) => KakaoCircle
  /**
   * 마커 클러스터러. styles 는 클러스터 원에 그대로 들어가는 CSS (calculator 구간 순서대로),
   * minLevel 이상 레벨에서만 묶는다. disableClickZoom 이 false 면 클러스터를 눌렀을 때 한 단계 확대한다
   */
  MarkerClusterer?: new (options: {
    map: KakaoMapInstance
    averageCenter?: boolean
    minLevel?: number
    gridSize?: number
    minClusterSize?: number
    disableClickZoom?: boolean
    calculator?: number[]
    styles?: Partial<CSSStyleDeclaration>[]
  }) => KakaoMarkerClusterer
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
    /** 선 모양 (solid, shortdash 등) */
    strokeStyle?: string
    map?: KakaoMapInstance
  }) => KakaoOverlay
  /** 마커 클릭, 지도 이동·확대가 끝남(idle) 등 지도 객체 이벤트 */
  event: {
    addListener(target: KakaoOverlay, type: 'click', handler: () => void): void
    removeListener(target: KakaoOverlay, type: 'click', handler: () => void): void
    /** 클러스터를 다시 계산해 그린 뒤 (clusters: 지금 지도에 그려진 클러스터들) */
    addListener(target: KakaoMarkerClusterer, type: 'clustered', handler: (clusters: KakaoCluster[]) => void): void
    addListener(target: KakaoMapInstance, type: 'idle' | 'click' | 'zoom_changed', handler: () => void): void
    removeListener(target: KakaoMapInstance, type: 'idle' | 'click' | 'zoom_changed', handler: () => void): void
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
