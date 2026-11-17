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
 * OpenRouteService(ORS) 길찾기 API 로 도보(WALK) 이동시간과 경로 좌표를 가져온다 (REQ-022, REQ-041).
 * <p>
 * 요청: GET https://api.heigit.org/openrouteservice/v2/directions/foot-walking?start=경도,위도&end=경도,위도
 *       헤더 Authorization: {ORS API 키}   (카카오와 달리 앞에 붙이는 말 없이 키만)
 * <p>
 * 옛 주소 api.openrouteservice.org 는 2026-04 부터 지원 중단(호출 한도 축소)이라 새 주소 api.heigit.org 를 쓴다.
 * <p>
 * 구조는 KakaoMobilityClient 와 같다.
 * - 실패(HTTP 오류, 타임아웃, 경로 없음)는 예외 대신 Optional.empty() 로 알린다
 * - 직선거리로 바꾸는 일은 이동시간 서비스(3-5)가 한다
 * API 키는 application-local.yml 의 ors.api-key 에서 읽는다. 코드·로그에 키를 남기지 않는다.
 */
@Slf4j
@Component
public class OrsClient {

    private static final String BASE_URL = "https://api.heigit.org/openrouteservice";
    private static final String WALK_DIRECTIONS_PATH = "/v2/directions/foot-walking";

    /** 외부 API 가 응답하지 않을 때 무한정 기다리지 않도록 제한 (차량과 같은 값) */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    /**
     * 기본 주소·인증 헤더·타임아웃을 한 번 정해 둔다.
     * 생성자로 키를 받기 때문에 테스트에서는 new OrsClient("test-key") 로 스프링 없이 만들 수 있다.
     * <p>
     * ${ors.api-key:} 끝의 콜론(:)은 "설정이 없으면 빈 문자열" 이라는 뜻이다.
     * ORS 키를 아직 yml 에 안 넣은 팀원도 서버가 켜지게 하려고 붙였다 (그 경우 호출은 실패 → 직선거리로 대체).
     */
    public OrsClient(@Value("${ors.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, apiKey) // ORS 는 키만 넣는다
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 두 지점 사이 도보 이동시간과 경로를 조회한다.
     * 매개변수는 우리 규칙(위도 먼저)으로 받고, ORS 에 보낼 때만 "경도,위도" 로 바꾼다.
     *
     * @return 성공하면 TravelRoute, 실패하면 Optional.empty()
     */
    public Optional<TravelRoute> findWalkRoute(double fromLatitude, double fromLongitude,
                                               double toLatitude, double toLongitude) {
        try {
            OrsDirectionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(WALK_DIRECTIONS_PATH)
                            .queryParam("start", fromLongitude + "," + fromLatitude) // 경도,위도 순서!
                            .queryParam("end", toLongitude + "," + toLatitude)
                            .build())
                    .retrieve()                              // 4xx·5xx 면 RestClientException 발생
                    .body(OrsDirectionsResponse.class);      // JSON → 응답 그릇
            return toTravelRoute(response);
        } catch (RestClientException e) {
            // 경로 없음(404), 키 오류(401·403), 한도 초과(429), 타임아웃이 모두 여기로 온다
            log.warn("ORS 도보 길찾기 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * ORS 응답 → 우리 결과(TravelRoute) 변환.
     * 네트워크 없이 테스트할 수 있게 따로 뺐다 (package-private).
     */
    Optional<TravelRoute> toTravelRoute(OrsDirectionsResponse response) {
        // 1. 경로가 하나도 없으면 실패
        if (response == null || response.features() == null || response.features().isEmpty()) {
            return Optional.empty();
        }

        // 2. 시간 정보가 없으면 실패 (시간 없이는 코스를 짤 수 없음)
        OrsDirectionsResponse.Feature feature = response.features().get(0);
        if (feature.properties() == null || feature.properties().summary() == null) {
            log.warn("ORS 도보 길찾기 응답에 소요시간이 없음");
            return Optional.empty();
        }

        // 3. 초(소수) → 분, 올림 (1536.7초 = 25.6분 → 26분). duration 이 이미 double 이라 60.0 으로 나눠도 되고 60 도 된다
        int travelMinutes = (int) Math.ceil(feature.properties().summary().duration() / 60.0);

        // 4. 경로 좌표 모으기
        List<Coordinate> coordinates = extractCoordinates(feature.geometry());

        return Optional.of(new TravelRoute(travelMinutes, coordinates));
    }

    /**
     * geometry.coordinates 의 [경도, 위도] 쌍을 위도·경도 Coordinate 로 바꾼다.
     * 카카오와 달리 이미 쌍으로 묶여 있고 도로별로 나뉘지 않아서, 겹치는 점을 지울 필요가 없다.
     */
    private List<Coordinate> extractCoordinates(OrsDirectionsResponse.Geometry geometry) {
        List<Coordinate> coordinates = new ArrayList<>();
        if (geometry == null || geometry.coordinates() == null) {
            return coordinates;
        }

        for (List<Double> pair : geometry.coordinates()) {
            // 고도가 붙으면 [경도, 위도, 고도] 3칸이 올 수 있어 2칸 미만만 건너뛴다
            if (pair == null || pair.size() < 2) {
                continue;
            }
            double longitude = pair.get(0); // 0번 칸 = 경도(x)
            double latitude = pair.get(1);  // 1번 칸 = 위도(y)
            coordinates.add(new Coordinate(latitude, longitude));
        }
        return coordinates;
    }
}
