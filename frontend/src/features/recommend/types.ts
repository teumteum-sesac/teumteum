import { getTransportLabel, type CategoryCode, type LocationValue, type TransportType } from '../../shared/types/domain'
import { addMinutesToTime } from '../../shared/utils/format'
import type { RouteLegResponse } from '../map/routePath'

// TODO(나혜빈): 추천 API 명세가 나오면 백엔드 DTO 이름·필드와 맞춘다. (현재 초안)

/** 코스 안의 장소 1곳 */
export interface CoursePlace {
  placeId: string
  name: string
  category: CategoryCode
  subCategory: string
  address: string
  openingHours?: string
  /** 사진 (TourAPI, 전시·공연은 포스터). 없으면 카테고리 기본 이미지 */
  imageUrl?: string
  description?: string
  lat: number
  lng: number
  /** 이전 지점(출발지 또는 앞 장소)에서 이동시간(분) */
  travelMinutes: number
  /** 대중교통일 때 이전 지점에서 오는 노선·환승 (예: "버스 721 · 환승 0회") */
  transitInfo?: string
  /** 예상 체류시간(분) */
  stayMinutes: number
  /** 방문 순서 (1부터) */
  visitOrder: number
}

/** 추천 코스 (장소 1곳 이상) */
export interface Course {
  courseId: string
  places: CoursePlace[]
  origin: LocationValue
  destination: LocationValue | null
  transportType: TransportType
  /** 출발 시각 "HH:mm" */
  startTime: string
  /** 마지막 장소 → 도착지 이동시간(분). 도착지 없으면 0 */
  finalTravelMinutes: number
  /** 이동 포함 총 소요시간(분) */
  totalMinutes: number
  /** true면 이동시간 API 실패로 직선거리 기준 '예상 시간' */
  estimated?: boolean
}

/** 장소 교체(DET-03) 후보 */
export interface AlternativePlace extends CoursePlace {
  /** 이 장소로 바꿨을 때 총 소요시간 변화(분). 음수면 줄어듦 */
  diffMinutes: number
}

/** 코스명: 장소 이름 조합 */
export function getCourseName(course: Course): string {
  return course.places.map((p) => p.name).join(' → ')
}

export interface ScheduleRow {
  place: CoursePlace
  /** 도착 시각 "HH:mm" */
  arrive: string
  /** 떠나는 시각 "HH:mm" */
  leave: string
}

/** 출발 시각부터 장소별 방문 시각을 계산한다. endTime은 마지막 장소(또는 도착지) 도착 시각 */
export function buildSchedule(course: Course): { rows: ScheduleRow[]; endTime: string } {
  const rows: ScheduleRow[] = []
  let clock = course.startTime
  for (const place of course.places) {
    const arrive = addMinutesToTime(clock, place.travelMinutes)
    const leave = addMinutesToTime(arrive, place.stayMinutes)
    rows.push({ place, arrive, leave })
    clock = leave
  }
  return { rows, endTime: addMinutesToTime(clock, course.finalTravelMinutes) }
}

/** 700m 이내 대중교통 구간을 걸어갈 때 백엔드가 보내는 transitInfo */
const WALK_TRANSIT_INFO = '도보'

/**
 * 대중교통 노선 정보(transitInfo)를 노선 목록과 환승 문구로 나눈다.
 * 백엔드(OdsayClient)가 만드는 모양:
 * - "수도권 2호선 · 환승 0회"                → lines ["수도권 2호선"], transfer "환승 0회"
 * - "버스 3411 → 수도권 2호선 · 환승 1회"    → lines ["버스 3411", "수도권 2호선"], transfer "환승 1회"
 * - "환승 0회" (노선 이름을 못 찾음)          → lines [], transfer "환승 0회"
 * - "도보" (700m 이내 구간)                   → lines [], transfer null
 */
export function parseTransitInfo(transitInfo: string): { lines: string[]; transfer: string | null } {
  if (transitInfo === WALK_TRANSIT_INFO) return { lines: [], transfer: null }
  const [linePart, transfer = null] = transitInfo.split(' · ')
  if (linePart.startsWith('환승 ')) return { lines: [], transfer: linePart }
  return { lines: linePart.split(' → '), transfer }
}

/**
 * 구간 이동 수단 이름: "버스" · "지하철" · "버스+지하철" · "도보".
 * 백엔드는 버스 노선에만 "버스 " 를 붙이므로(예: "버스 3411"), "버스 " 로 시작하지 않는 노선은 지하철로 본다
 * ("신분당선", "공항철도" 처럼 "호선" 이 없는 노선도 지하철로 잡힌다).
 * 노선 정보가 없으면(도보·차량, 또는 대중교통 API 실패) 코스 이동수단 이름 ("대중교통" 등).
 */
export function getLegMode(course: Course, transitInfo?: string): string {
  if (!transitInfo) return getTransportLabel(course.transportType)
  if (transitInfo === WALK_TRANSIT_INFO) return '도보'

  const { lines } = parseTransitInfo(transitInfo)
  if (lines.length === 0) return getTransportLabel(course.transportType)

  const hasBus = lines.some((line) => line.startsWith('버스 '))
  const hasSubway = lines.some((line) => !line.startsWith('버스 '))
  if (hasBus && hasSubway) return '버스+지하철'
  return hasBus ? '버스' : '지하철'
}

/** 장소 간 이동시간 합계(분) */
export function getTotalTravelMinutes(course: Course): number {
  return course.places.reduce((sum, p) => sum + p.travelMinutes, 0) + course.finalTravelMinutes
}

// ===== 추천 API 응답 (백엔드 recommend/dto 와 같은 이름·필드, docs/recommend-api.md) =====
// 화면 컴포넌트는 위의 Course·CoursePlace 를 그대로 쓰고, API 응답은 아래 to... 함수로 바꿔서 넘긴다.

/** 위치 (LocationResponse) */
export interface LocationResponse {
  name: string | null
  address: string | null
  latitude: number
  longitude: number
}

/** 실제로 적용된 조건 (RecommendConditionResponse) */
export interface RecommendConditionResponse {
  origin: LocationResponse
  destination: LocationResponse | null
  startTime: string
  availableMinutes: number
  transportType: TransportType
  categoryMode: 'DIRECT' | 'RANDOM'
  categories: CategoryCode[]
}

/** 세분류 필터 (SubCategoryFilterResponse, REQ-050) */
export interface SubCategoryFilterResponse {
  category: CategoryCode
  categoryName: string
  subCategories: string[]
}

/** 코스 안의 장소 (RecommendPlaceResponse) */
export interface RecommendPlaceResponse {
  placeId: string
  visitOrder: number
  placeName: string
  category: CategoryCode
  subCategory: string
  address: string | null
  latitude: number
  longitude: number
  imageUrl: string | null
  openingHours: string | null
  stayMinutes: number
  travelMinutes: number
  transitInfo: string | null
}

/** 추천 코스 1개 (RecommendCourseResponse) */
export interface RecommendCourseResponse {
  recommendationId: string
  courseName: string
  placeCount: number
  startTime: string
  totalMinutes: number
  totalTravelMinutes: number
  finalTravelMinutes: number
  estimated: boolean
  places: RecommendPlaceResponse[]
}

/** 추천 생성·다시 추천 응답 (RecommendResponse) */
export interface RecommendResponse {
  recommendationSetId: string
  /** 보관 만료 시각 (생성 + 2시간) */
  expiresAt: string
  condition: RecommendConditionResponse
  subCategoryFilters: SubCategoryFilterResponse[]
  courses: RecommendCourseResponse[]
}

/** 코스 상세의 장소 (RecommendPlaceDetailResponse) */
export interface RecommendPlaceDetailResponse extends RecommendPlaceResponse {
  description: string | null
  kakaoMapUrl: string | null
}

/** 추천 코스 상세·장소 교체 응답 (RecommendCourseDetailResponse) */
export interface RecommendCourseDetailResponse {
  recommendationId: string
  recommendationSetId: string
  expiresAt: string
  origin: LocationResponse
  destination: LocationResponse | null
  transportType: TransportType
  availableMinutes: number
  courseName: string
  placeCount: number
  startTime: string
  totalMinutes: number
  totalTravelMinutes: number
  finalTravelMinutes: number
  estimated: boolean
  places: RecommendPlaceDetailResponse[]
  routeLegs: RouteLegResponse[]
}

/** 장소 교체 후보 (AlternativePlaceResponse) */
export interface AlternativePlaceResponse {
  placeId: string
  placeName: string
  category: CategoryCode
  subCategory: string
  address: string | null
  latitude: number
  longitude: number
  imageUrl: string | null
  stayMinutes: number
  travelMinutes: number
  transitInfo: string | null
  /** 바꾸면 총 소요시간 변화(분). 음수면 줄어듦 */
  diffMinutes: number
  /** 바꾸면 이용 시간을 넘는지 (넘으면 서버가 교체를 거절: 409 AVAILABLE_TIME_EXCEEDED) */
  exceedsAvailableTime: boolean
  estimated: boolean
}

/** 장소 교체 후보 목록 (AlternativePlaceListResponse) */
export interface AlternativePlaceListResponse {
  recommendationId: string
  currentPlace: {
    placeId: string
    visitOrder: number
    placeName: string
    category: CategoryCode
    subCategory: string
    stayMinutes: number
  }
  alternatives: AlternativePlaceResponse[]
}

/** 장소 교체 요청 (PlaceReplaceRequest) */
export interface PlaceReplaceRequest {
  targetPlaceId: string
  newPlaceId: string
}

function toLocationValue(location: LocationResponse, fallbackName: string): LocationValue {
  return {
    name: location.name ?? fallbackName,
    address: location.address ?? undefined,
    lat: location.latitude,
    lng: location.longitude,
  }
}

function toCoursePlace(place: RecommendPlaceResponse | RecommendPlaceDetailResponse): CoursePlace {
  return {
    placeId: place.placeId,
    name: place.placeName,
    category: place.category,
    subCategory: place.subCategory,
    address: place.address ?? '주소 정보 없음',
    openingHours: place.openingHours ?? undefined,
    imageUrl: place.imageUrl ?? undefined,
    description: 'description' in place ? (place.description ?? undefined) : undefined,
    lat: place.latitude,
    lng: place.longitude,
    travelMinutes: place.travelMinutes,
    transitInfo: place.transitInfo ?? undefined,
    stayMinutes: place.stayMinutes,
    visitOrder: place.visitOrder,
  }
}

/** 추천 목록의 코스 → 화면용 Course. 출발지·도착지·이동수단은 응답의 condition 에서 가져온다 */
export function toCourse(course: RecommendCourseResponse, condition: RecommendConditionResponse): Course {
  return {
    courseId: course.recommendationId,
    places: course.places.map(toCoursePlace),
    origin: toLocationValue(condition.origin, '출발지'),
    destination: condition.destination ? toLocationValue(condition.destination, '도착지') : null,
    transportType: condition.transportType,
    startTime: course.startTime,
    finalTravelMinutes: course.finalTravelMinutes,
    totalMinutes: course.totalMinutes,
    estimated: course.estimated,
  }
}

/** 코스 상세(또는 교체 결과) → 화면용 Course */
export function detailToCourse(detail: RecommendCourseDetailResponse): Course {
  return {
    courseId: detail.recommendationId,
    places: detail.places.map(toCoursePlace),
    origin: toLocationValue(detail.origin, '출발지'),
    destination: detail.destination ? toLocationValue(detail.destination, '도착지') : null,
    transportType: detail.transportType,
    startTime: detail.startTime,
    finalTravelMinutes: detail.finalTravelMinutes,
    totalMinutes: detail.totalMinutes,
    estimated: detail.estimated,
  }
}
