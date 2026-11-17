package com.teumteum.recommend.client;

import com.teumteum.recommend.client.OrsDirectionsResponse.Feature;
import com.teumteum.recommend.client.OrsDirectionsResponse.Geometry;
import com.teumteum.recommend.client.OrsDirectionsResponse.Properties;
import com.teumteum.recommend.client.OrsDirectionsResponse.Summary;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ORS 응답 → TravelRoute 변환 테스트.
 * <p>
 * 실제 ORS 서버는 부르지 않는다. 가짜 응답 객체를 만들어 toTravelRoute() 에 넣고 결과만 확인한다.
 * 테스트 데이터는 Postman 으로 받은 성수역 → 서울숲 도보 응답(1536.7초)에서 일부를 줄여 쓴다.
 */
class OrsClientTest {

    // 생성자는 RestClient 설정만 하고 요청은 보내지 않으므로 가짜 키를 넣어도 된다
    private final OrsClient client = new OrsClient("test-key");

    @Test
    @DisplayName("소요시간 1536.7초는 올림해서 26분이다")
    void toTravelRoute_convertsSecondsToMinutes_roundingUp() {
        OrsDirectionsResponse response = successResponse(1536.7, List.of(
                List.of(127.055879, 37.544556),
                List.of(127.055168, 37.544767)
        ));

        Optional<TravelRoute> result = client.toTravelRoute(response);

        assertTrue(result.isPresent());
        assertEquals(26, result.get().travelMinutes());
    }

    @Test
    @DisplayName("딱 나누어떨어지는 1200초는 그대로 20분이다")
    void toTravelRoute_keepsExactMinutes() {
        OrsDirectionsResponse response = successResponse(1200.0, List.of(
                List.of(127.055879, 37.544556),
                List.of(127.055168, 37.544767)
        ));

        assertEquals(20, client.toTravelRoute(response).get().travelMinutes());
    }

    @Test
    @DisplayName("coordinates 의 [경도, 위도] 쌍을 [위도, 경도] 좌표로 바꾼다")
    void toTravelRoute_swapsLongitudeLatitudeOrder() {
        // ORS: 0번 칸 경도(127...), 1번 칸 위도(37...)
        OrsDirectionsResponse response = successResponse(1536.7, List.of(
                List.of(127.055879, 37.544556),
                List.of(127.055168, 37.544767),
                List.of(127.037398, 37.544403)
        ));

        List<Coordinate> coordinates = client.toTravelRoute(response).get().coordinates();

        // 서울은 위도 약 37, 경도 약 127 → 위도 칸에 37 이 들어가야 맞다
        assertEquals(List.of(
                new Coordinate(37.544556, 127.055879),
                new Coordinate(37.544767, 127.055168),
                new Coordinate(37.544403, 127.037398)
        ), coordinates);
    }

    @Test
    @DisplayName("고도가 붙은 [경도, 위도, 고도] 도 앞의 두 칸만 쓴다")
    void toTravelRoute_ignoresElevation() {
        OrsDirectionsResponse response = successResponse(1536.7, List.of(
                List.of(127.055879, 37.544556, 15.0)
        ));

        List<Coordinate> coordinates = client.toTravelRoute(response).get().coordinates();

        assertEquals(List.of(new Coordinate(37.544556, 127.055879)), coordinates);
    }

    @Test
    @DisplayName("응답이 null 이거나 경로 목록이 비면 실패(빈 결과)다")
    void toTravelRoute_returnsEmpty_whenResponseHasNoFeature() {
        assertTrue(client.toTravelRoute(null).isEmpty());
        assertTrue(client.toTravelRoute(new OrsDirectionsResponse(null)).isEmpty());
        assertTrue(client.toTravelRoute(new OrsDirectionsResponse(List.of())).isEmpty());
    }

    @Test
    @DisplayName("소요시간(summary)이 없으면 실패(빈 결과)다")
    void toTravelRoute_returnsEmpty_whenSummaryMissing() {
        Feature feature = new Feature(
                new Geometry(List.of(List.of(127.055879, 37.544556))),
                new Properties(null)
        );

        assertTrue(client.toTravelRoute(new OrsDirectionsResponse(List.of(feature))).isEmpty());
    }

    @Test
    @DisplayName("소요시간은 있는데 경로 좌표가 없으면 시간만 있고 좌표는 빈 목록이다")
    void toTravelRoute_returnsEmptyCoordinates_whenGeometryMissing() {
        Feature feature = new Feature(null, new Properties(new Summary(2134.3, 1536.7)));

        TravelRoute result = client.toTravelRoute(new OrsDirectionsResponse(List.of(feature))).get();

        assertEquals(26, result.travelMinutes());
        assertTrue(result.coordinates().isEmpty());
    }

    /**
     * 성공 응답을 만드는 도우미.
     * 테스트마다 바뀌는 것은 소요시간(초)과 좌표뿐이라 나머지는 여기서 채운다.
     */
    private OrsDirectionsResponse successResponse(double durationSeconds, List<List<Double>> coordinates) {
        Feature feature = new Feature(
                new Geometry(coordinates),
                new Properties(new Summary(2134.3, durationSeconds))
        );
        return new OrsDirectionsResponse(List.of(feature));
    }
}
