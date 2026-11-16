package com.teumteum.recommend.dto;

import java.util.List;

/**
 * 추천 코스 1개. RES-01 의 카드 1장.
 * <pre>
 * {
 *   "recommendationId": "rc_41b9",
 *   "courseName": "당구장 A → 소품샵 B",
 *   "placeCount": 2,
 *   "startTime": "13:00",
 *   "totalMinutes": 160, "totalTravelMinutes": 30, "finalTravelMinutes": 7,
 *   "estimated": false,
 *   "places": [ { RecommendPlaceResponse }, ... ]
 * }
 * </pre>
 * 시간 관계: totalTravelMinutes = places 의 travelMinutes 합 + finalTravelMinutes
 *           totalMinutes       = places 의 stayMinutes 합 + totalTravelMinutes  (CourseTimeCalculator 로 계산)
 * (docs/recommend-api.md 1.3)
 */
public record RecommendCourseResponse(
        String recommendationId,              // 코스 1개의 ID. 상세·교체·저장 API 에서 쓴다
        String courseName,                    // 장소 이름을 " → " 로 이은 코스명
        int placeCount,                       // 장소 수 1 ~ 4
        String startTime,                     // 출발 시각 "HH:mm"
        int totalMinutes,                     // 총 소요시간(분)
        int totalTravelMinutes,               // 이동시간 합계(분), 마지막 → 도착지 포함
        int finalTravelMinutes,               // 마지막 장소 → 도착지 이동(분). 도착지가 없으면 0
        boolean estimated,                    // 직선거리로 추정한 구간이 있으면 true ("예상 시간" 표시)
        List<RecommendPlaceResponse> places   // 방문 순서대로
) {
}
