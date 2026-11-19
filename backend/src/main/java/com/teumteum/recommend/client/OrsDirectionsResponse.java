package com.teumteum.recommend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * OpenRouteService 길찾기 API(GET /v2/directions/foot-walking) 응답을 그대로 받는 그릇.
 * <p>
 * 응답은 GeoJSON(지도 데이터 표준 형식)이다. 실제로는 bbox, segments(안내 문구), warnings, metadata 도 오지만
 * 우리가 쓰는 칸만 적는다.
 * <pre>
 * features[0]
 *  ├─ properties.summary.duration   소요시간(초, 소수. 예: 1536.7)
 *  └─ geometry.coordinates[]        경로 좌표 [[경도, 위도], [경도, 위도], ...] 쌍의 목록
 * </pre>
 * 카카오와 달리 성공 코드 칸이 없다. 실패하면 HTTP 4xx·5xx 로 오고, RestClient 가 예외로 바꿔 준다.
 * 이 클래스는 client 패키지 안에서만 쓰고, 바깥에는 TravelRoute 로 바꿔서 내보낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrsDirectionsResponse(
        List<Feature> features
) {

    /** 경로 1개. 대안 경로를 요청하지 않으므로 1개만 온다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(
            Geometry geometry,
            Properties properties
    ) {
    }

    /** 지도에 그릴 선 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            List<List<Double>> coordinates // 바깥 목록 = 점들, 안쪽 목록 = [경도, 위도] 한 쌍
    ) {
    }

    /** 경로 정보 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            Summary summary
    ) {
    }

    /** 경로 전체 요약. 카카오와 달리 소수로 온다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            double distance, // 거리(미터)
            double duration  // 소요시간(초)
    ) {
    }
}
