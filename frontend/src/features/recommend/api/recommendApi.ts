import { apiGet, apiPost } from '../../../shared/api/request'
import type { RecommendRequest } from '../../condition/recommendRequest'
import type {
  AlternativePlaceListResponse,
  PlaceReplaceRequest,
  RecommendCourseDetailResponse,
  RecommendResponse,
} from '../types'

/*
 * [이 파일이 하는 일]
 * 추천 API 호출 함수 (나혜빈 #114·#121·#125·#126·#133, docs/recommend-api.md). 모두 로그인 없이 쓴다.
 * 실패하면 ApiError 가 던져진다. 자주 보는 코드:
 *   404 RECOMMENDATION_NOT_FOUND  없는 추천 ID
 *   410 RECOMMENDATION_EXPIRED    2시간 보관이 지남 → 만료 화면
 *   429 TOO_MANY_REQUESTS         다시 추천을 너무 자주 누름
 *   409 AVAILABLE_TIME_EXCEEDED   교체하면 이용 시간을 넘음
 */

/** 추천 생성 (CON-01 [추천받기] → RES-01). 맞는 코스가 없으면 courses: [] */
export function createRecommendation(body: RecommendRequest) {
  return apiPost<RecommendResponse>('/recommendations', body)
}

/** 다시 추천 (RES-01). 새 recommendationSetId 로 응답 */
export function regenerateRecommendation(recommendationSetId: string) {
  return apiPost<RecommendResponse>(`/recommendations/${encodeURIComponent(recommendationSetId)}/regenerate`)
}

/** 추천 코스 상세 (DET-01 일정, DET-02 지도 경로) */
export function getRecommendCourse(recommendationId: string) {
  return apiGet<RecommendCourseDetailResponse>(`/recommendations/courses/${encodeURIComponent(recommendationId)}`)
}

/** 장소 교체 후보 (DET-03) */
export function getAlternatives(recommendationId: string, placeId: string) {
  return apiGet<AlternativePlaceListResponse>(
    `/recommendations/courses/${encodeURIComponent(recommendationId)}/places/${encodeURIComponent(placeId)}/alternatives`,
  )
}

/** 장소 교체 확정 (DET-03). 바뀐 코스 상세를 돌려준다 */
export function replacePlace(recommendationId: string, body: PlaceReplaceRequest) {
  return apiPost<RecommendCourseDetailResponse>(`/recommendations/courses/${encodeURIComponent(recommendationId)}/replace`, body)
}
