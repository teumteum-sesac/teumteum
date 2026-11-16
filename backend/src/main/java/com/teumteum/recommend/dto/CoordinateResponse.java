package com.teumteum.recommend.dto;

/**
 * 지도 경로의 좌표 1개.
 * <pre>
 * { "latitude": 37.5446, "longitude": 127.0559 }
 * </pre>
 */
public record CoordinateResponse(
        double latitude,   // 위도
        double longitude   // 경도
) {
}
