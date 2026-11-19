import type { CategoryCode, TransportType } from '../../shared/types/domain'

export interface CourseSaveRequest { recommendationId: string }
export interface CourseSaveResponse {
  courseId: string
  courseName: string
  createdAt: string
  created: boolean
}

export interface MemberResponse { email: string; nickname: string }
export interface MemberUpdateRequest {
  nickname: string
  newPassword?: string
  newPasswordConfirm?: string
}
// 동의 체크(agreed=true)와 현재 비밀번호를 둘 다 보내야 탈퇴된다
export interface MemberWithdrawRequest { password: string; agreed: boolean }
export interface MemberWithdrawResponse { withdrawn: boolean }

// 백엔드 CourseSummaryResponse에 대응하는 코스 요약 타입이다.
export interface CourseSummaryResponse {
    courseId: string
    sourceRecommendationId: string
    courseName: string
    placeCount: number
    totalMinutes: number
    createdAt: string
}

// 백엔드 CourseListResponse에 대응한다.
export interface CourseListResponse {
    // 현재 페이지의 코스 요약 목록
    content: CourseSummaryResponse[]

    // 현재 페이지 번호. 첫 페이지는 0
    page: number

    // 한 페이지에 요청한 개수
    size: number

    // 본인의 전체 저장 코스 수
    totalElements: number

    // 전체 페이지 수
    totalPages: number

    // 다음 페이지 존재 여부
    hasNext: boolean
}

// 백엔드 CourseDeleteResponse에 대응한다.
export interface CourseDeleteResponse {
    // 삭제한 저장 코스 ID
    courseId: string

    // 삭제 성공 여부
    deleted: boolean
}

export interface CourseCoordinateResponse {
  latitude: number
  longitude: number
}

export interface CourseLocationResponse extends CourseCoordinateResponse {
  name: string | null
  address: string | null
}

export interface CoursePlaceResponse extends CourseCoordinateResponse {
  coursePlaceId: string
  placeId: string
  visitOrder: number
  placeName: string
  category: CategoryCode
  subCategory: string
  address: string | null
  imageUrl: string | null
  description: string | null
  openingHours: string | null
  stayMinutes: number
  travelMinutes: number
  transitInfo: string | null
  /** 카카오 장소 ID (장소 상세 화면의 카카오 장소 정보용). 카카오 장소가 아니면 null */
  kakaoPlaceId: string | null
}

export interface CourseRouteLegResponse {
  fromVisitOrder: number
  toVisitOrder: number
  pathType: 'ACTUAL' | 'STRAIGHT'
  coordinates: CourseCoordinateResponse[]
}

// 저장 당시 상세·지도 정보. 최신 장소 정보와 합치지 않는다.
export interface CourseResponse {
  courseId: string
  sourceRecommendationId: string
  courseName: string
  createdAt: string
  origin: CourseLocationResponse
  destination: CourseLocationResponse | null
  transportType: TransportType
  startTime: string
  totalMinutes: number
  totalTravelMinutes: number
  finalTravelMinutes: number
  estimated: boolean
  places: CoursePlaceResponse[]
  routeLegs: CourseRouteLegResponse[]
}
