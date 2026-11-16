package com.teumteum.recommend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오모빌리티 길찾기 API(GET /v1/directions) 응답을 그대로 받는 그릇.
 * <p>
 * 실제 응답에는 trans_id, bound, fare, guides 등 많은 값이 있지만 우리가 쓰는 칸만 적는다.
 * <pre>
 * routes[0]
 *  ├─ result_code       0 이면 성공, 그 외는 실패 (예: 104 출발지·도착지가 너무 가까움)
 *  ├─ summary.duration  소요시간(초)
 *  └─ sections[] → roads[] → vertexes[]   경로 좌표 [경도, 위도, 경도, 위도, ...] 한 줄 배열
 * </pre>
 * 이 클래스는 client 패키지 안에서만 쓰고, 바깥에는 TravelRoute 로 바꿔서 내보낸다.
 * <p>
 * - @JsonIgnoreProperties(ignoreUnknown = true): 적지 않은 칸(fare 등)이 와도 오류 없이 무시
 * - @JsonProperty("result_code"): JSON 은 snake_case, 자바 필드는 camelCase 라 이름을 이어 준다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoDirectionsResponse(
        List<Route> routes
) {

    /** 경로 1개. 우리는 기본 옵션(추천 경로)으로 1개만 받는다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(
            @JsonProperty("result_code") int resultCode,
            @JsonProperty("result_msg") String resultMsg,
            Summary summary,       // 실패면 null 일 수 있음
            List<Section> sections // 실패면 null 일 수 있음
    ) {
    }

    /** 경로 전체 요약 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            int distance, // 거리(미터)
            int duration  // 소요시간(초)
    ) {
    }

    /** 구간. 경유지가 없으면 1개 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(
            List<Road> roads
    ) {
    }

    /** 도로 1개 (예: 아차산로) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Road(
            List<Double> vertexes // [경도, 위도, 경도, 위도, ...] → 2개씩 끊어 읽는다
    ) {
    }
}
