package com.teumteum.recommend.dto;

import com.teumteum.recommend.domain.PathType;

import java.util.List;

/**
 * 지도 경로의 한 구간 (DET-02 코스 지도). 출발지 → 장소1, 장소1 → 장소2, ... , 마지막 장소 → 도착지.
 * <pre>
 * { "fromVisitOrder": 0, "toVisitOrder": 1, "pathType": "STRAIGHT",
 *   "coordinates": [ { "latitude": 37.5446, "longitude": 127.0559 }, { "latitude": 37.5470, "longitude": 127.0500 } ] }
 * </pre>
 * visitOrder 번호: 출발지 0, 장소 1 ~ N, 도착지가 있으면 N + 1.
 * 모양은 저장 코스(course-api.md 3.2)의 CourseRouteLegResponse 와 같다. 코스 저장 때 그대로 넘길 수 있게 맞춤.
 */
public record RouteLegResponse(
        int fromVisitOrder,                    // 구간 시작 번호
        int toVisitOrder,                      // 구간 끝 번호
        PathType pathType,                     // ACTUAL(실제 경로) / STRAIGHT(직선)
        List<CoordinateResponse> coordinates   // 진행 방향 순서의 좌표, 2개 이상
) {
}
