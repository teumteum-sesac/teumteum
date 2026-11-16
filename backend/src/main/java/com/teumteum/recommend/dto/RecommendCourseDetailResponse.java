package com.teumteum.recommend.dto;

import com.teumteum.recommend.domain.TransportType;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 추천 코스 상세 (DET-01 일정, DET-02 지도). 장소 교체 API 의 응답으로도 쓴다.
 * GET  /api/recommendations/courses/{recommendationId}
 * POST /api/recommendations/courses/{recommendationId}/replace  (교체 후 새 recommendationId 로 응답)
 * <pre>
 * {
 *   "recommendationId": "rc_41b9", "recommendationSetId": "rs_8f2a", "expiresAt": "2026-11-13T15:00:00+09:00",
 *   "origin": { LocationResponse }, "destination": null,
 *   "transportType": "TRANSIT", "availableMinutes": 180,
 *   "courseName": "당구장 A → 소품샵 B", "placeCount": 2, "startTime": "13:00",
 *   "totalMinutes": 160, "totalTravelMinutes": 30, "finalTravelMinutes": 7, "estimated": false,
 *   "places": [ { RecommendPlaceDetailResponse }, ... ],
 *   "routeLegs": [ { RouteLegResponse }, ... ]
 * }
 * </pre>
 * RecommendCourseResponse 필드 + 상세 전용 필드. record 는 상속이 안 돼서 필드를 다시 적었다.
 * 이동시간은 추천할 때 계산해 보관한 값을 쓴다 (상세 조회에서 이동시간 API 를 다시 부르지 않음).
 * (docs/recommend-api.md 3)
 */
public record RecommendCourseDetailResponse(
        // ── 상세 전용 ──
        String recommendationId,                     // 코스 ID
        String recommendationSetId,                  // 소속 추천 묶음 ID
        OffsetDateTime expiresAt,                    // 보관 만료 시각
        LocationResponse origin,                     // 출발지
        LocationResponse destination,                // 도착지, 없으면 null
        TransportType transportType,                 // 이동수단
        int availableMinutes,                        // 이용 시간(분). 교체 시 시간 초과 판단에 사용

        // ── RecommendCourseResponse 와 같은 필드 ──
        String courseName,                           // 코스명
        int placeCount,                              // 장소 수 1 ~ 4
        String startTime,                            // 출발 시각 "HH:mm"
        int totalMinutes,                            // 총 소요시간(분)
        int totalTravelMinutes,                      // 이동시간 합계(분)
        int finalTravelMinutes,                      // 마지막 장소 → 도착지(분), 도착지 없으면 0
        boolean estimated,                           // 직선거리 추정 구간이 있으면 true

        List<RecommendPlaceDetailResponse> places,   // 방문 순서대로 (소개·카카오맵 링크 포함)
        List<RouteLegResponse> routeLegs             // 지도 경로 구간들
) {
}
