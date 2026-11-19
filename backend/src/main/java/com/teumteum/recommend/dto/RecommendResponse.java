package com.teumteum.recommend.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 추천 생성·다시 추천 API 의 응답 전체 (RES-01 화면 하나 분량).
 * POST /api/recommendations, POST /api/recommendations/{recommendationSetId}/regenerate
 * <pre>
 * {
 *   "recommendationSetId": "rs_8f2a",
 *   "expiresAt": "2026-11-13T15:00:00+09:00",
 *   "condition": { RecommendConditionResponse },
 *   "subCategoryFilters": [ { "category": "SPORTS", "categoryName": "스포츠활동", "subCategories": ["당구", "볼링"] }, ... ],
 *   "courses": [ { RecommendCourseResponse }, ... ]
 * }
 * </pre>
 * 맞는 코스가 없으면 오류가 아니라 courses: [] 로 200 을 돌려준다. (REQ-027)
 * 실제 응답은 ApiResponse.ok(...) 로 한 번 더 감싸서 { "success": true, "data": { 여기 } } 로 나간다.
 * (docs/recommend-api.md 1.3, 1.4)
 */
public record RecommendResponse(
        String recommendationSetId,               // 이번 추천 묶음 ID. 다시 추천에 쓴다
        OffsetDateTime expiresAt,                 // 보관 만료 시각 (생성 + 2시간). JSON 에는 "2026-11-13T15:00:00+09:00" 형식
        RecommendConditionResponse condition,     // 실제로 적용한 조건
        List<SubCategoryFilterResponse> subCategoryFilters, // 세분류 필터, 대분류별로 묶음 (REQ-050). '전체' 칩은 프론트가 붙인다
        List<RecommendCourseResponse> courses     // 추천 코스 0 ~ 3개, 정렬 없음
) {
}
