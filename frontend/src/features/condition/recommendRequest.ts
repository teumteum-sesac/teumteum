import type { CategoryCode, CategoryMode, LocationValue, TransportType } from '../../shared/types/domain'

/*
 * [이 파일이 하는 일]
 * 외출 조건(CON-01)에서 고른 값을 추천 생성 API 요청 모양(RecommendRequest)으로 바꾼다.
 *   POST /api/recommendations  (나혜빈 #114, docs/recommend-api.md 1.2)
 *
 * [역할 나눔]
 *   CON-01(김용현) : 조건을 모아 이 함수로 요청을 만든다 + [추천받기] 활성 조건
 *   RES-01(나혜빈) : 이 요청으로 API 를 부르고 결과·다시 추천을 보여 준다
 *     const request = toRecommendRequest(useConditionStore.getState())
 *     if (request) await apiPost<RecommendResponse>('/recommendations', request)
 *
 * 이름·필드는 백엔드 DTO 와 똑같이 맞춘다 (backend/.../recommend/dto/RecommendRequest.java, LocationRequest.java)
 */

/** 위치 1곳 (LocationRequest). name·address 는 화면 표시용이라 없을 수 있다 */
export interface LocationRequest {
  name: string | null
  address: string | null
  latitude: number
  longitude: number
}

/** 추천 생성 요청 (RecommendRequest) */
export interface RecommendRequest {
  origin: LocationRequest
  /** 도착지. 없으면 출발지 주변에서 끝나는 코스 */
  destination: LocationRequest | null
  /** 시작 시각 "HH:mm", 30분 단위 */
  startTime: string
  /** 이용 시간(분) 30 ~ 360 */
  availableMinutes: number
  transportType: TransportType
  categoryMode: CategoryMode
  /** 대분류 0 ~ 3개. RANDOM 이면 서버가 무시 */
  categories: CategoryCode[]
}

/** 백엔드 검증 규칙 (RecommendRequest.java 와 같은 값). 어기면 서버가 400 을 준다 */
export const MIN_AVAILABLE_MINUTES = 30
export const MAX_AVAILABLE_MINUTES = 360
const START_TIME_PATTERN = /^([01]\d|2[0-3]):(00|30)$/

/** toRecommendRequest 가 읽는 조건 값 (condition/store 의 상태와 같은 이름) */
export interface ConditionValues {
  origin: LocationValue | null
  destination: LocationValue | null
  startTime: string
  availableMinutes: number
  transportType: TransportType
  categoryMode: CategoryMode
  categories: CategoryCode[]
}

function toLocationRequest(location: LocationValue): LocationRequest {
  return {
    name: location.name || null,
    address: location.address ?? null,
    latitude: location.lat,
    longitude: location.lng,
  }
}

/**
 * 지금 조건으로 추천을 요청할 수 있는지. [추천받기] 버튼 활성 조건과 같다.
 * - 출발지 필수 (REQ-007)
 * - 이용 시간 30분 ~ 6시간 (0분이면 비활성, REQ-008)
 * - 시작 시각 30분 단위
 */
export function canRequestRecommend(values: Pick<ConditionValues, 'origin' | 'availableMinutes' | 'startTime'>): boolean {
  return (
    values.origin !== null &&
    values.availableMinutes >= MIN_AVAILABLE_MINUTES &&
    values.availableMinutes <= MAX_AVAILABLE_MINUTES &&
    START_TIME_PATTERN.test(values.startTime)
  )
}

/**
 * 조건 → 추천 요청. 보낼 수 없는 조건이면 null (canRequestRecommend 와 같은 기준).
 * - 직접 선택인데 아무것도 안 골랐으면 그대로 DIRECT + 빈 배열로 보낸다 → 서버가 RANDOM 으로 처리 (recommend-api.md 1.2)
 * - RANDOM 이면 categories 는 빈 배열로 보낸다 (서버가 무시하지만 헷갈리지 않게)
 */
export function toRecommendRequest(values: ConditionValues): RecommendRequest | null {
  if (!canRequestRecommend(values) || values.origin === null) return null
  return {
    origin: toLocationRequest(values.origin),
    destination: values.destination ? toLocationRequest(values.destination) : null,
    startTime: values.startTime,
    availableMinutes: values.availableMinutes,
    transportType: values.transportType,
    categoryMode: values.categoryMode,
    categories: values.categoryMode === 'RANDOM' ? [] : values.categories,
  }
}
