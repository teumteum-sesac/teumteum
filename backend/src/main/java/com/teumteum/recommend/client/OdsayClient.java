package com.teumteum.recommend.client;

import com.teumteum.recommend.domain.TravelRoute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ODsay 대중교통 길찾기 API 로 대중교통(TRANSIT) 이동시간과 노선·환승 정보를 가져온다 (REQ-022, REQ-025).
 * <p>
 * 요청: GET https://api.odsay.com/v1/api/searchPubTransPathT?SX=경도&SY=위도&EX=경도&EY=위도&apiKey=키
 * <p>
 * 차량·도보와 다른 점
 * - API 키를 헤더가 아니라 쿼리 파라미터(apiKey)로 보낸다
 * - 실패해도 HTTP 200 이고 result 대신 error 가 온다 → result 가 없으면 실패로 본다
 * - 소요시간이 이미 분 단위다 (변환 없음)
 * - 경로 좌표는 따로 부르지 않고 같은 응답의 정류장 좌표로 만든다 (출발지 → 지나는 정류장 → 도착지).
 *   정류장 좌표가 하나도 없으면 빈 목록 → 지도에서는 직선(STRAIGHT)으로 그린다
 * - 서버 플랫폼 키라 ODsay 에 호출하는 곳의 공인 IP 를 등록해야 한다
 * - 무료 호출 한도가 있어 최종 후보 코스에만 부른다 (NFR-014, 이동시간 서비스에서 지킴)
 * - 하루 한도와 별개로 짧은 시간에 연달아 부르면 429 "Too Many Requests" 로 막는다
 *   → 호출 사이에 최소 간격(MIN_INTERVAL)을 둔다. 연달아 6번까지는 통과, 300ms 간격이면 통과 (2026-11-18 실측)
 * <p>
 * 실패는 예외 대신 Optional.empty() 로 알리고, 직선거리로 바꾸는 일은 이동시간 서비스(3-5)가 한다.
 * API 키는 application-local.yml 의 odsay.api-key 에서 읽는다. 코드·로그에 키를 남기지 않는다.
 */
@Slf4j
@Component
public class OdsayClient {

    private static final String BASE_URL = "https://api.odsay.com/v1/api";
    private static final String TRANSIT_PATH = "/searchPubTransPathT";

    /** 외부 API 가 응답하지 않을 때 무한정 기다리지 않도록 제한 (차량·도보와 같은 값) */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    /** subPath.trafficType 값 */
    private static final int TRAFFIC_TYPE_SUBWAY = 1;
    private static final int TRAFFIC_TYPE_BUS = 2;

    /** 호출 사이 최소 간격 (429 방지). 실측에서 300ms 간격은 모두 통과했다 */
    static final Duration MIN_INTERVAL = Duration.ofMillis(300);

    private final RestClient restClient;
    private final String apiKey;
    private final RequestThrottle throttle = new RequestThrottle(MIN_INTERVAL);

    /**
     * 기본 주소·타임아웃·인코딩 방식을 한 번 정해 둔다.
     * 생성자로 키를 받기 때문에 테스트에서는 new OdsayClient("test-key") 로 스프링 없이 만들 수 있다.
     * ${odsay.api-key:} 끝의 콜론(:)은 "설정이 없으면 빈 문자열" → 키가 없는 팀원도 서버가 켜진다 (호출만 실패).
     * <p>
     * 키를 헤더로 넣지 않으므로 필드에 보관했다가 요청할 때마다 쿼리 파라미터로 붙인다.
     */
    public OdsayClient(@Value("${odsay.api-key:}") String apiKey) {
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        // ODsay 키에는 + / = 같은 문자가 들어갈 수 있다.
        // URL 에서 + 는 공백으로 읽힐 수 있어서, 그대로 보내면 키가 달라져 인증 실패(ApiKeyAuthFailed)가 난다.
        // TEMPLATE_AND_VALUES: {변수} 자리에 들어가는 값은 특수문자까지 전부 %XX 로 인코딩한다 (+ → %2B)
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(BASE_URL);
        uriBuilderFactory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);

        this.restClient = RestClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 두 지점 사이 대중교통 이동시간과 노선·환승 정보를 조회한다.
     * 매개변수는 우리 규칙(위도 먼저)으로 받고, ODsay 에 보낼 때만 X=경도, Y=위도 로 넣는다.
     *
     * @return 성공하면 TravelRoute(분, 경로 좌표, transitInfo), 실패하면 Optional.empty()
     */
    public Optional<TravelRoute> findTransitRoute(double fromLatitude, double fromLongitude,
                                                  double toLatitude, double toLongitude) {
        throttle.acquire(); // 직전 호출에서 300ms 가 안 지났으면 기다렸다가 부른다
        try {
            OdsayDirectionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(TRANSIT_PATH)
                            .queryParam("SX", fromLongitude) // X = 경도
                            .queryParam("SY", fromLatitude)  // Y = 위도
                            .queryParam("EX", toLongitude)
                            .queryParam("EY", toLatitude)
                            .queryParam("apiKey", "{apiKey}") // {apiKey} 자리에 아래 값이 인코딩되어 들어간다
                            .build(apiKey))
                    .retrieve()                              // 4xx·5xx 면 RestClientException 발생
                    .body(OdsayDirectionsResponse.class);    // JSON → 응답 그릇
            return toTravelRoute(response,
                    new TravelRoute.Coordinate(fromLatitude, fromLongitude),
                    new TravelRoute.Coordinate(toLatitude, toLongitude));
        } catch (RestClientException e) {
            // 타임아웃, 서버 오류(5xx) 등이 여기로 온다. 키 오류는 200 + error 라서 toTravelRoute 에서 걸러진다
            log.warn("ODsay 대중교통 길찾기 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 출발·도착 좌표 없이 변환 (경로 좌표는 정류장만으로 만든다). 기존 테스트용 */
    Optional<TravelRoute> toTravelRoute(OdsayDirectionsResponse response) {
        return toTravelRoute(response, null, null);
    }

    /**
     * ODsay 응답 → 우리 결과(TravelRoute) 변환.
     * 네트워크 없이 테스트할 수 있게 따로 뺐다 (package-private).
     *
     * @param from 출발 지점 (경로선 맨 앞에 붙인다. 없으면 null)
     * @param to   도착 지점 (경로선 맨 뒤에 붙인다. 없으면 null)
     */
    Optional<TravelRoute> toTravelRoute(OdsayDirectionsResponse response,
                                        TravelRoute.Coordinate from, TravelRoute.Coordinate to) {
        // 1. result 가 없으면 실패 (키 오류, IP 미등록, 출발·도착이 너무 가까움 등은 200 + error 로 온다)
        if (response == null || response.result() == null) {
            // 오류 내용(예: 700m 이내 -98, 429 Too Many Requests, Daily quota exceeded)을 남겨야 원인을 바로 안다
            log.warn("ODsay 대중교통 길찾기 결과 없음: {}", response == null ? "응답 없음" : response.error());
            return Optional.empty();
        }

        // 2. 경로 후보가 하나도 없으면 실패
        List<OdsayDirectionsResponse.Path> paths = response.result().path();
        if (paths == null || paths.isEmpty()) {
            return Optional.empty();
        }

        // 3. ODsay 추천 순서대로 오므로 첫 번째 경로를 쓴다 (팀 결정)
        OdsayDirectionsResponse.Path path = paths.get(0);
        if (path.info() == null) {
            log.warn("ODsay 대중교통 길찾기 응답에 소요시간이 없음");
            return Optional.empty();
        }

        int travelMinutes = path.info().totalTime(); // 이미 분 단위
        String transitInfo = buildTransitInfo(path);

        // 4. 지도 경로선: 출발지 → 지하철·버스가 지나는 정류장 → 도착지 (정류장 좌표가 없으면 빈 목록 = 직선)
        return Optional.of(new TravelRoute(travelMinutes, buildPathCoordinates(path.subPath(), from, to), transitInfo));
    }

    /**
     * 지도에 그릴 경로 좌표. 구간 순서대로 지하철·버스가 지나는 정류장 좌표를 잇고, 앞뒤에 출발·도착 지점을 붙인다.
     * - 정류장 목록이 있으면 정류장 좌표, 없으면 탄 곳·내린 곳 좌표
     * - 도보 구간은 ODsay 가 좌표를 주지 않으므로 앞뒤 점을 직선으로 잇는다
     * - 지하철·버스 좌표를 하나도 못 얻으면 빈 목록 (지도에서 출발→도착 직선, STRAIGHT)
     */
    List<TravelRoute.Coordinate> buildPathCoordinates(List<OdsayDirectionsResponse.SubPath> subPaths,
                                                      TravelRoute.Coordinate from, TravelRoute.Coordinate to) {
        List<TravelRoute.Coordinate> rideCoordinates = new ArrayList<>();
        if (subPaths != null) {
            for (OdsayDirectionsResponse.SubPath subPath : subPaths) {
                if (subPath == null || (subPath.trafficType() != TRAFFIC_TYPE_SUBWAY && subPath.trafficType() != TRAFFIC_TYPE_BUS)) {
                    continue; // 도보 구간
                }
                rideCoordinates.addAll(rideCoordinates(subPath));
            }
        }
        if (rideCoordinates.isEmpty()) {
            return List.of();
        }

        List<TravelRoute.Coordinate> path = new ArrayList<>();
        if (from != null) {
            path.add(from);
        }
        path.addAll(rideCoordinates);
        if (to != null) {
            path.add(to);
        }
        return path;
    }

    /** 지하철·버스 구간 1개의 좌표: 지나는 정류장 → 없으면 탄 곳·내린 곳 */
    private List<TravelRoute.Coordinate> rideCoordinates(OdsayDirectionsResponse.SubPath subPath) {
        List<TravelRoute.Coordinate> coordinates = new ArrayList<>();
        if (subPath.passStopList() != null && subPath.passStopList().stations() != null) {
            for (OdsayDirectionsResponse.Station station : subPath.passStopList().stations()) {
                TravelRoute.Coordinate c = station == null ? null : parseCoordinate(station.y(), station.x());
                if (c != null) {
                    coordinates.add(c);
                }
            }
        }
        if (!coordinates.isEmpty()) {
            return coordinates;
        }
        if (subPath.startX() != null && subPath.startY() != null) {
            coordinates.add(new TravelRoute.Coordinate(subPath.startY(), subPath.startX()));
        }
        if (subPath.endX() != null && subPath.endY() != null) {
            coordinates.add(new TravelRoute.Coordinate(subPath.endY(), subPath.endX()));
        }
        return coordinates;
    }

    /** 문자열 위도·경도 → 좌표. 비었거나 숫자가 아니면 null (그 정류장만 건너뛴다) */
    private TravelRoute.Coordinate parseCoordinate(String latitude, String longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        try {
            return new TravelRoute.Coordinate(Double.parseDouble(latitude.trim()), Double.parseDouble(longitude.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 노선·환승 문장을 만든다. 예) "수도권 2호선 · 환승 0회", "버스 3411 → 버스 461 · 환승 1회"
     * 노선 이름을 하나도 못 찾으면 환승 횟수만 쓴다. 예) "환승 0회"
     */
    private String buildTransitInfo(OdsayDirectionsResponse.Path path) {
        List<String> lineNames = extractLineNames(path.subPath());
        int transferCount = countTransfers(path.info());

        String transferText = "환승 " + transferCount + "회";
        if (lineNames.isEmpty()) {
            return transferText;
        }
        return String.join(" → ", lineNames) + " · " + transferText;
    }

    /**
     * 탄 노선 이름을 순서대로 모은다. 도보 구간(trafficType 3)은 건너뛴다.
     * lane 이 여러 개면 "이 중 아무거나 타도 됨" 이라 첫 번째만 쓴다.
     */
    private List<String> extractLineNames(List<OdsayDirectionsResponse.SubPath> subPaths) {
        List<String> lineNames = new ArrayList<>();
        if (subPaths == null) {
            return lineNames;
        }

        for (OdsayDirectionsResponse.SubPath subPath : subPaths) {
            if (subPath == null || subPath.lane() == null || subPath.lane().isEmpty()) {
                continue; // 도보 구간이거나 노선 정보가 없음
            }
            OdsayDirectionsResponse.Lane lane = subPath.lane().get(0);

            if (subPath.trafficType() == TRAFFIC_TYPE_SUBWAY && lane.name() != null) {
                lineNames.add(lane.name());                // "수도권 2호선"
            } else if (subPath.trafficType() == TRAFFIC_TYPE_BUS && lane.busNo() != null) {
                lineNames.add("버스 " + lane.busNo());     // "버스 3411"
            }
        }
        return lineNames;
    }

    /**
     * 환승 횟수 = 탄 횟수 - 1.
     * ODsay 의 busTransitCount·subwayTransitCount 는 이름과 달리 "탑승 횟수" 다.
     * 예) 2호선만 1번 탐 → subwayTransitCount 1 → 환승 0회 / 버스 2번 탐 → busTransitCount 2 → 환승 1회
     * 탄 횟수가 0 으로 와도 -1 이 되지 않게 0 아래로는 내려가지 않는다.
     */
    int countTransfers(OdsayDirectionsResponse.Info info) {
        int rideCount = info.busTransitCount() + info.subwayTransitCount();
        return Math.max(0, rideCount - 1);
    }
}
