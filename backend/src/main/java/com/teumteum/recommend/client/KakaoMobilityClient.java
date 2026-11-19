package com.teumteum.recommend.client;

import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 카카오모빌리티 길찾기 API 로 차량(CAR) 이동시간과 경로 좌표를 가져온다 (REQ-022, REQ-041).
 * <p>
 * 요청: GET https://apis-navi.kakaomobility.com/v1/directions?origin=경도,위도&destination=경도,위도
 *       헤더 Authorization: KakaoAK {REST API 키}
 * <p>
 * 실패(HTTP 오류, 타임아웃, result_code ≠ 0, 경로 없음)는 예외를 던지지 않고 Optional.empty() 로 알린다.
 * 실패 시 직선거리로 바꾸는 일은 이동시간 서비스(3-5)가 한다. 이 클래스는 "API 호출과 변환"만 책임진다.
 * <p>
 * API 키는 application-local.yml 의 kakao.rest-api-key 에서 읽는다. 코드·로그에 키를 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoMobilityClient {

    private static final String BASE_URL = "https://apis-navi.kakaomobility.com";
    private static final String DIRECTIONS_PATH = "/v1/directions";
    private static final int SUCCESS_CODE = 0;

    /** 외부 API 가 응답하지 않을 때 무한정 기다리지 않도록 제한 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3); // 연결까지
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);    // 응답 받기까지

    private final RestClient restClient;

    /**
     * 스프링이 객체를 만들 때 설정 파일의 키를 넣어 준다 (@Value).
     * 기본 주소·인증 헤더·타임아웃을 한 번 정해 두고, 요청마다 다시 적지 않는다.
     */
    public KakaoMobilityClient(@Value("${kakao.rest-api-key}") String restApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey) // KakaoAK 뒤 띄어쓰기 필수
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 두 지점 사이 차량 이동시간과 경로를 조회한다.
     * 매개변수는 우리 규칙(위도 먼저)으로 받고, 카카오에 보낼 때만 "경도,위도" 로 바꾼다.
     *
     * @return 성공하면 TravelRoute, 실패하면 Optional.empty()
     */
    public Optional<TravelRoute> findCarRoute(double fromLatitude, double fromLongitude,
                                              double toLatitude, double toLongitude) {
        try {
            KakaoDirectionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(DIRECTIONS_PATH)
                            .queryParam("origin", fromLongitude + "," + fromLatitude)       // 경도,위도 순서!
                            .queryParam("destination", toLongitude + "," + toLatitude)
                            .build())
                    .retrieve()                                // 4xx·5xx 면 RestClientException 발생
                    .body(KakaoDirectionsResponse.class);      // JSON → 응답 그릇
            return toTravelRoute(response);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패·HTTP 오류·JSON 변환 실패가 모두 여기로 온다. 키가 찍히지 않게 메시지만 남긴다
            log.warn("카카오모빌리티 길찾기 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 카카오 응답 → 우리 결과(TravelRoute) 변환.
     * 네트워크 없이 테스트할 수 있게 따로 뺐다 (package-private: 같은 패키지의 테스트에서 호출 가능).
     */
    Optional<TravelRoute> toTravelRoute(KakaoDirectionsResponse response) {
        // 1. 경로가 하나도 없으면 실패
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }

        // 2. 첫 경로의 결과 코드가 0 이 아니면 실패 (예: 104 출발지·도착지가 5m 이내)
        KakaoDirectionsResponse.Route route = response.routes().get(0);
        if (route.resultCode() != SUCCESS_CODE || route.summary() == null) {
            log.warn("카카오모빌리티 길찾기 결과 없음: code={}, msg={}", route.resultCode(), route.resultMsg());
            return Optional.empty();
        }

        // 3. 초 → 분, 올림 (670초 = 11.17분 → 12분). 60.0 으로 나눠야 소수가 남는다 (60 이면 정수 나눗셈)
        int travelMinutes = (int) Math.ceil(route.summary().duration() / 60.0);

        // 4. 경로 좌표 모으기
        List<Coordinate> coordinates = extractCoordinates(route.sections());

        return Optional.of(new TravelRoute(travelMinutes, coordinates));
    }

    /**
     * sections → roads → vertexes 를 차례로 돌며 좌표를 한 줄로 모은다.
     * - vertexes 는 [경도, 위도, 경도, 위도, ...] 라서 2칸씩 건너뛰며 읽는다
     * - 앞 도로의 끝점과 다음 도로의 시작점이 같으므로, 바로 앞 좌표와 같으면 넣지 않는다
     */
    private List<Coordinate> extractCoordinates(List<KakaoDirectionsResponse.Section> sections) {
        List<Coordinate> coordinates = new ArrayList<>();
        if (sections == null) {
            return coordinates;
        }

        for (KakaoDirectionsResponse.Section section : sections) {
            if (section.roads() == null) {
                continue;
            }
            for (KakaoDirectionsResponse.Road road : section.roads()) {
                List<Double> vertexes = road.vertexes();
                if (vertexes == null) {
                    continue;
                }
                // i + 1 < size: 짝이 맞지 않는 마지막 1칸은 버린다
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    double longitude = vertexes.get(i);     // 짝수 칸 = 경도(x)
                    double latitude = vertexes.get(i + 1);  // 홀수 칸 = 위도(y)
                    Coordinate point = new Coordinate(latitude, longitude);

                    // record 는 값이 같으면 equals 가 true → 겹치는 점 제거
                    if (coordinates.isEmpty() || !coordinates.getLast().equals(point)) {
                        coordinates.add(point);
                    }
                }
            }
        }
        return coordinates;
    }
}
