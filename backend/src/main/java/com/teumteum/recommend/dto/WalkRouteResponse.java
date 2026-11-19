package com.teumteum.recommend.dto;

import java.util.List;

/**
 * 두 지점 사이 도보 경로 (장소 상세 지도에 "내 위치 → 가게" 길을 그릴 때).
 * <pre>
 * { "travelMinutes": 6, "estimated": false, "path": [ { "latitude": 37.58, "longitude": 127.04 }, ... ] }
 * </pre>
 * estimated 가 true 면 경로 API 가 실패해 직선거리로 추정한 값이고, path 는 출발·도착 두 점(직선)이다.
 */
public record WalkRouteResponse(
        int travelMinutes,
        boolean estimated,
        List<CoordinateResponse> path
) {
}
