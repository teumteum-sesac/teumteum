package com.teumteum.recommend.dto;

import java.util.List;

/**
 * 장소 교체 후보 API 의 응답 전체 (DET-03 화면 하나 분량).
 * GET /api/recommendations/courses/{recommendationId}/places/{placeId}/alternatives
 * <pre>
 * {
 *   "recommendationId": "rc_41b9",
 *   "currentPlace": { CurrentPlaceResponse },
 *   "alternatives": [ { AlternativePlaceResponse }, ... ]
 * }
 * </pre>
 * 후보: 같은 세분류(없으면 같은 대분류) 중 코스에 없는 장소, 앞뒤 장소와 가까운 순으로 5곳 정도.
 * 후보가 없으면 alternatives: [] 로 200.
 * (docs/recommend-api.md 4)
 */
public record AlternativePlaceListResponse(
        String recommendationId,                      // 교체하려는 코스 ID
        CurrentPlaceResponse currentPlace,            // 바꾸려는 현재 장소
        List<AlternativePlaceResponse> alternatives   // 교체 후보들
) {
}
