package com.teumteum.recommend.client;

import com.teumteum.recommend.client.OdsayDirectionsResponse.Info;
import com.teumteum.recommend.client.OdsayDirectionsResponse.Lane;
import com.teumteum.recommend.client.OdsayDirectionsResponse.PassStopList;
import com.teumteum.recommend.client.OdsayDirectionsResponse.Path;
import com.teumteum.recommend.client.OdsayDirectionsResponse.Result;
import com.teumteum.recommend.client.OdsayDirectionsResponse.Station;
import com.teumteum.recommend.client.OdsayDirectionsResponse.SubPath;
import com.teumteum.recommend.domain.TravelRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ODsay 응답 → TravelRoute 변환 테스트.
 * <p>
 * 실제 ODsay 서버는 부르지 않는다. 가짜 응답 객체를 만들어 toTravelRoute() 에 넣고 결과만 확인한다.
 * 테스트 데이터는 Postman 으로 받은 잠실역 → 낙성대역 응답에서 필요한 칸만 줄여 쓴다.
 */
class OdsayClientTest {

    // 생성자는 RestClient 설정만 하고 요청은 보내지 않으므로 가짜 키를 넣어도 된다
    private final OdsayClient client = new OdsayClient("test-key");

    // 자주 쓰는 구간들 (trafficType 1 지하철 · 2 버스 · 3 도보)
    private static final SubPath WALK = new SubPath(3, 2, null);
    private static final SubPath SUBWAY_LINE_2 = new SubPath(1, 22, List.of(new Lane("수도권 2호선", null)));
    private static final SubPath BUS_3411 = new SubPath(2, 19, List.of(new Lane(null, "3411")));
    private static final SubPath BUS_461 = new SubPath(2, 32, List.of(new Lane(null, "461")));

    // 경로선 앞뒤에 붙는 출발·도착 지점 (잠실역 → 낙성대역 근처)
    private static final TravelRoute.Coordinate FROM = new TravelRoute.Coordinate(37.5133, 127.1001);
    private static final TravelRoute.Coordinate TO = new TravelRoute.Coordinate(37.4766, 126.9636);

    @Test
    @DisplayName("지하철 한 번만 타면 소요시간은 totalTime 그대로, 환승 0회다")
    void toTravelRoute_subwayOnly() {
        // Postman path[0]: 도보 → 2호선 → 도보, subwayTransitCount 1
        Path path = new Path(1, new Info(25, 0, 1), List.of(WALK, SUBWAY_LINE_2, WALK));

        TravelRoute result = client.toTravelRoute(responseOf(path)).get();

        assertEquals(25, result.travelMinutes());
        assertEquals("수도권 2호선 · 환승 0회", result.transitInfo());
    }

    @Test
    @DisplayName("버스 두 번 타면 노선을 순서대로 잇고 환승 1회다")
    void toTravelRoute_busTransfer() {
        // Postman path[1]: 버스 3411 → 버스 461, busTransitCount 2
        Path path = new Path(2, new Info(54, 2, 0), List.of(WALK, BUS_3411, WALK, BUS_461, WALK));

        TravelRoute result = client.toTravelRoute(responseOf(path)).get();

        assertEquals(54, result.travelMinutes());
        assertEquals("버스 3411 → 버스 461 · 환승 1회", result.transitInfo());
    }

    @Test
    @DisplayName("버스와 지하철을 섞어 타면 두 탑승 횟수를 더해서 환승을 센다")
    void toTravelRoute_busAndSubway() {
        Path path = new Path(3, new Info(40, 1, 1), List.of(WALK, BUS_3411, WALK, SUBWAY_LINE_2, WALK));

        assertEquals("버스 3411 → 수도권 2호선 · 환승 1회",
                client.toTravelRoute(responseOf(path)).get().transitInfo());
    }

    @Test
    @DisplayName("한 구간에 노선이 여러 개면(아무거나 타도 됨) 첫 번째만 쓴다")
    void toTravelRoute_usesFirstLaneOnly() {
        // Postman path[3]: 9303 또는 2000 중 아무거나
        SubPath busEither = new SubPath(2, 19, List.of(new Lane(null, "9303"), new Lane(null, "2000")));
        Path path = new Path(2, new Info(57, 1, 0), List.of(WALK, busEither, WALK));

        assertEquals("버스 9303 · 환승 0회", client.toTravelRoute(responseOf(path)).get().transitInfo());
    }

    @Test
    @DisplayName("경로 후보가 여러 개면 ODsay 추천 순서대로 첫 번째(path[0])를 쓴다")
    void toTravelRoute_usesFirstPath() {
        Path first = new Path(1, new Info(25, 0, 1), List.of(SUBWAY_LINE_2));
        Path second = new Path(2, new Info(20, 1, 0), List.of(BUS_3411)); // 더 짧아도 첫 번째를 쓴다

        assertEquals(25, client.toTravelRoute(new OdsayDirectionsResponse(new Result(List.of(first, second))))
                .get().travelMinutes());
    }

    @Test
    @DisplayName("지하철·버스 구간에 좌표가 하나도 없으면 경로 좌표는 빈 목록이다 (지도에서 직선)")
    void toTravelRoute_hasNoCoordinates_whenNoStopCoordinates() {
        Path path = new Path(1, new Info(25, 0, 1), List.of(WALK, SUBWAY_LINE_2, WALK));

        assertTrue(client.toTravelRoute(responseOf(path), FROM, TO).get().coordinates().isEmpty());
    }

    @Test
    @DisplayName("지나는 정류장 좌표가 있으면 출발지 → 정류장 순서 → 도착지로 경로를 만든다")
    void toTravelRoute_buildsPathFromStations() {
        SubPath subway = new SubPath(1, 22, List.of(new Lane("수도권 2호선", null)), 127.1000, 37.5130, 126.9630, 37.4770,
                new PassStopList(List.of(new Station("127.1000", "37.5130"), new Station("127.0500", "37.5000"), new Station("126.9630", "37.4770"))));
        Path path = new Path(1, new Info(25, 0, 1), List.of(WALK, subway, WALK));

        List<TravelRoute.Coordinate> coordinates = client.toTravelRoute(responseOf(path), FROM, TO).get().coordinates();

        assertEquals(List.of(FROM, new TravelRoute.Coordinate(37.5130, 127.1000), new TravelRoute.Coordinate(37.5000, 127.0500),
                new TravelRoute.Coordinate(37.4770, 126.9630), TO), coordinates);
    }

    @Test
    @DisplayName("정류장 목록이 없으면 탄 곳·내린 곳 좌표를 쓰고, 환승하면 구간을 순서대로 잇는다")
    void toTravelRoute_usesStartEnd_whenNoStations() {
        SubPath bus = new SubPath(2, 19, List.of(new Lane(null, "3411")), 127.10, 37.51, 127.05, 37.50, null);
        SubPath subway = new SubPath(1, 10, List.of(new Lane("수도권 2호선", null)), 127.04, 37.50, 126.96, 37.48, new PassStopList(null));
        Path path = new Path(3, new Info(40, 1, 1), List.of(WALK, bus, WALK, subway, WALK));

        List<TravelRoute.Coordinate> coordinates = client.toTravelRoute(responseOf(path), FROM, TO).get().coordinates();

        assertEquals(List.of(FROM, new TravelRoute.Coordinate(37.51, 127.10), new TravelRoute.Coordinate(37.50, 127.05),
                new TravelRoute.Coordinate(37.50, 127.04), new TravelRoute.Coordinate(37.48, 126.96), TO), coordinates);
    }

    @Test
    @DisplayName("좌표 문자열이 비었거나 숫자가 아닌 정류장은 건너뛴다")
    void toTravelRoute_skipsInvalidStations() {
        SubPath subway = new SubPath(1, 22, List.of(new Lane("수도권 2호선", null)), null, null, null, null,
                new PassStopList(List.of(new Station("127.1000", "37.5130"), new Station("", "37.5"), new Station("abc", "37.5"), new Station("126.9630", "37.4770"))));
        Path path = new Path(1, new Info(25, 0, 1), List.of(subway));

        assertEquals(List.of(new TravelRoute.Coordinate(37.5130, 127.1000), new TravelRoute.Coordinate(37.4770, 126.9630)),
                client.toTravelRoute(responseOf(path)).get().coordinates());
    }

    @Test
    @DisplayName("오류 응답(result 없음)이거나 경로 후보가 없으면 실패(빈 결과)다")
    void toTravelRoute_returnsEmpty_whenNoResult() {
        assertTrue(client.toTravelRoute(null).isEmpty());
        assertTrue(client.toTravelRoute(new OdsayDirectionsResponse(null)).isEmpty()); // {"error": ...}
        assertTrue(client.toTravelRoute(new OdsayDirectionsResponse(new Result(null))).isEmpty());
        assertTrue(client.toTravelRoute(new OdsayDirectionsResponse(new Result(List.of()))).isEmpty());
    }

    @Test
    @DisplayName("소요시간(info)이 없으면 실패(빈 결과)다")
    void toTravelRoute_returnsEmpty_whenInfoMissing() {
        Path path = new Path(1, null, List.of(SUBWAY_LINE_2));

        Optional<TravelRoute> result = client.toTravelRoute(responseOf(path));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("노선 정보(subPath)가 없으면 환승 횟수만 쓴다")
    void toTravelRoute_transferOnly_whenNoLineNames() {
        Path path = new Path(1, new Info(25, 0, 1), null);

        assertEquals("환승 0회", client.toTravelRoute(responseOf(path)).get().transitInfo());
    }

    @Test
    @DisplayName("탑승 횟수가 0 으로 와도 환승은 -1 이 아니라 0회다")
    void countTransfers_neverNegative() {
        assertEquals(0, client.countTransfers(new Info(10, 0, 0)));
    }

    /** 경로 1개짜리 성공 응답을 만드는 도우미 */
    private OdsayDirectionsResponse responseOf(Path path) {
        return new OdsayDirectionsResponse(new Result(List.of(path)));
    }
}
