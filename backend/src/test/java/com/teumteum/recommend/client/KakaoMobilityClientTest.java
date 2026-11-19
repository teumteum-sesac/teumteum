package com.teumteum.recommend.client;

import com.teumteum.recommend.client.KakaoDirectionsResponse.Road;
import com.teumteum.recommend.client.KakaoDirectionsResponse.Route;
import com.teumteum.recommend.client.KakaoDirectionsResponse.Section;
import com.teumteum.recommend.client.KakaoDirectionsResponse.Summary;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 카카오모빌리티 응답 → TravelRoute 변환 테스트.
 * <p>
 * 실제 카카오 서버는 부르지 않는다. 가짜 응답 객체를 만들어 toTravelRoute() 에 넣고 결과만 확인한다.
 * 그래서 인터넷·API 키 없이도 돌고, 몇 번을 돌려도 결과가 같다.
 * (실제 호출은 Postman 으로 확인했고, 3-5 이동시간 서비스 작업 때 서버를 띄워 다시 확인한다)
 * <p>
 * 테스트 데이터는 Postman 으로 받은 성수역 → 서울숲 응답(670초)에서 일부를 줄여 쓴다.
 */
class KakaoMobilityClientTest {

    // 생성자는 RestClient 설정만 하고 요청은 보내지 않으므로 가짜 키를 넣어도 된다
    private final KakaoMobilityClient client = new KakaoMobilityClient("test-key");

    @Test
    @DisplayName("소요시간 670초는 올림해서 12분이다")
    void toTravelRoute_convertsSecondsToMinutes_roundingUp() {
        KakaoDirectionsResponse response = successResponse(670, List.of(
                new Road(List.of(127.0559, 37.5446, 127.0570, 37.5442))
        ));

        Optional<TravelRoute> result = client.toTravelRoute(response);

        assertTrue(result.isPresent());
        assertEquals(12, result.get().travelMinutes());
    }

    @Test
    @DisplayName("딱 나누어떨어지는 600초는 그대로 10분이다")
    void toTravelRoute_keepsExactMinutes() {
        KakaoDirectionsResponse response = successResponse(600, List.of(
                new Road(List.of(127.0559, 37.5446, 127.0570, 37.5442))
        ));

        Optional<TravelRoute> result = client.toTravelRoute(response);

        assertEquals(10, result.get().travelMinutes());
    }

    @Test
    @DisplayName("vertexes 의 [경도, 위도] 를 [위도, 경도] 좌표로 바꾼다")
    void toTravelRoute_swapsLongitudeLatitudeOrder() {
        // 카카오: 짝수 칸 경도(127...), 홀수 칸 위도(37...)
        KakaoDirectionsResponse response = successResponse(670, List.of(
                new Road(List.of(127.0559, 37.5446, 127.0570, 37.5442))
        ));

        List<Coordinate> coordinates = client.toTravelRoute(response).get().coordinates();

        assertEquals(2, coordinates.size());
        // 서울은 위도 약 37, 경도 약 127 → 위도 칸에 37 이 들어가야 맞다
        assertEquals(new Coordinate(37.5446, 127.0559), coordinates.get(0));
        assertEquals(new Coordinate(37.5442, 127.0570), coordinates.get(1));
    }

    @Test
    @DisplayName("도로가 바뀌는 지점의 겹치는 좌표는 하나만 남긴다")
    void toTravelRoute_removesDuplicatePointBetweenRoads() {
        // 아차산로의 끝점(127.0570, 37.5442) = 성수이로의 시작점
        KakaoDirectionsResponse response = successResponse(670, List.of(
                new Road(List.of(127.0559, 37.5446, 127.0570, 37.5442)),
                new Road(List.of(127.0570, 37.5442, 127.0548, 37.5388))
        ));

        List<Coordinate> coordinates = client.toTravelRoute(response).get().coordinates();

        // 4개가 아니라 3개
        assertEquals(List.of(
                new Coordinate(37.5446, 127.0559),
                new Coordinate(37.5442, 127.0570),
                new Coordinate(37.5388, 127.0548)
        ), coordinates);
    }

    @Test
    @DisplayName("result_code 가 0 이 아니면 실패(빈 결과)다")
    void toTravelRoute_returnsEmpty_whenResultCodeIsNotZero() {
        // 예: 104 = 출발지와 도착지가 5m 이내. 실패면 summary·sections 가 오지 않는다
        Route failedRoute = new Route(104, "출발지와 도착지가 너무 가까움", null, null);
        KakaoDirectionsResponse response = new KakaoDirectionsResponse(List.of(failedRoute));

        Optional<TravelRoute> result = client.toTravelRoute(response);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("응답이 null 이거나 경로 목록이 비면 실패(빈 결과)다")
    void toTravelRoute_returnsEmpty_whenResponseHasNoRoute() {
        assertTrue(client.toTravelRoute(null).isEmpty());
        assertTrue(client.toTravelRoute(new KakaoDirectionsResponse(null)).isEmpty());
        assertTrue(client.toTravelRoute(new KakaoDirectionsResponse(List.of())).isEmpty());
    }

    @Test
    @DisplayName("성공인데 경로 좌표가 없으면 시간만 있고 좌표는 빈 목록이다")
    void toTravelRoute_returnsEmptyCoordinates_whenSectionsMissing() {
        Route route = new Route(0, "길찾기 성공", new Summary(2516, 670), null);
        KakaoDirectionsResponse response = new KakaoDirectionsResponse(List.of(route));

        TravelRoute result = client.toTravelRoute(response).get();

        assertEquals(12, result.travelMinutes());
        assertTrue(result.coordinates().isEmpty());
    }

    /**
     * 성공 응답(result_code 0)을 만드는 도우미.
     * 테스트마다 바뀌는 것은 소요시간(초)과 도로 좌표뿐이라 나머지는 여기서 채운다.
     */
    private KakaoDirectionsResponse successResponse(int durationSeconds, List<Road> roads) {
        Route route = new Route(
                0,
                "길찾기 성공",
                new Summary(2516, durationSeconds),
                List.of(new Section(roads))
        );
        return new KakaoDirectionsResponse(List.of(route));
    }
}
