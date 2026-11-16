package com.teumteum.place.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.kakao.KakaoAddressResponse;
import com.teumteum.place.dto.kakao.KakaoCoord2AddressResponse;
import com.teumteum.place.dto.kakao.KakaoPlaceResponse;
import com.teumteum.place.exception.PlaceErrorCode;
import java.net.URI;
import java.time.Duration;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 카카오 로컬 API 호출 전담 클라이언트 (NFR-029).
 * REST API 키는 커밋되지 않는 application-local.yml 의 kakao.rest-api-key 에서 읽는다 (NFR-005).
 * 좌표 주의: 카카오는 x = 경도(longitude), y = 위도(latitude) 이다.
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private static final String BASE_URL = "https://dapi.kakao.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /** 키워드·카테고리 검색 size 최대값 */
    public static final int PLACE_MAX_SIZE = 15;
    /** 주소 검색 size 최대값 */
    public static final int ADDRESS_MAX_SIZE = 30;
    /** page 최대값 */
    public static final int MAX_PAGE = 45;
    /** 반경 최대값(m) */
    public static final int MAX_RADIUS = 20_000;

    private final RestClient restClient;

    public KakaoLocalClient(@Value("${kakao.rest-api-key:}") String restApiKey) {
        if (!StringUtils.hasText(restApiKey)) {
            throw new IllegalStateException(
                    "kakao.rest-api-key 가 비어 있습니다. application-local.yml 에 카카오 REST API 키를 넣으세요.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 키워드로 장소를 검색한다. 중심 좌표(lng, lat)를 주면 각 장소의 distance(m)가 함께 온다.
     */
    public KakaoPlaceResponse searchKeyword(String query, Double lng, Double lat, int page, int size) {
        return get("keyword", uri -> {
            uri.path("/v2/local/search/keyword.json")
                    .queryParam("query", query)
                    .queryParam("page", clamp(page, 1, MAX_PAGE))
                    .queryParam("size", clamp(size, 1, PLACE_MAX_SIZE));
            if (lng != null && lat != null) {
                uri.queryParam("x", lng).queryParam("y", lat);
            }
            return uri.build();
        }, KakaoPlaceResponse.class);
    }

    /**
     * 주소를 검색한다. 주소 형태로 입력했을 때만 결과가 나온다.
     */
    public KakaoAddressResponse searchAddress(String query, int page, int size) {
        return get("address", uri -> uri.path("/v2/local/search/address.json")
                .queryParam("query", query)
                .queryParam("page", clamp(page, 1, MAX_PAGE))
                .queryParam("size", clamp(size, 1, ADDRESS_MAX_SIZE))
                .build(), KakaoAddressResponse.class);
    }

    /**
     * 좌표를 주소로 바꾼다 (현재 위치, 지도 핀 위치 표시용).
     */
    public KakaoCoord2AddressResponse coordToAddress(double lng, double lat) {
        return get("coord2address", uri -> uri.path("/v2/local/geo/coord2address.json")
                .queryParam("x", lng)
                .queryParam("y", lat)
                .build(), KakaoCoord2AddressResponse.class);
    }

    /**
     * 중심 좌표 반경 안에서 카테고리로 장소를 검색한다 (REQ-015·016 에서 사용). 가까운 순으로 정렬한다.
     */
    public KakaoPlaceResponse searchCategory(KakaoCategory category, double lng, double lat,
                                             int radius, int page, int size) {
        return get("category", uri -> uri.path("/v2/local/search/category.json")
                .queryParam("category_group_code", category.name())
                .queryParam("x", lng)
                .queryParam("y", lat)
                .queryParam("radius", clamp(radius, 1, MAX_RADIUS))
                .queryParam("page", clamp(page, 1, MAX_PAGE))
                .queryParam("size", clamp(size, 1, PLACE_MAX_SIZE))
                .queryParam("sort", "distance")
                .build(), KakaoPlaceResponse.class);
    }

    private <T> T get(String apiName, Function<UriBuilder, URI> uriFunction, Class<T> responseType) {
        try {
            T body = restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        log.warn("[KakaoLocal] {} 호출 한도 초과 (429)", apiName);
                        throw new CustomException(PlaceErrorCode.KAKAO_API_RATE_LIMIT);
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            log.error("[KakaoLocal] {} 인증 실패 ({}). REST API 키와 앱 설정을 확인하세요.", apiName, status);
                        } else {
                            log.warn("[KakaoLocal] {} 호출 실패: status={}", apiName, status);
                        }
                        throw new CustomException(PlaceErrorCode.KAKAO_API_ERROR);
                    })
                    .body(responseType);
            if (body == null) {
                log.warn("[KakaoLocal] {} 응답 본문 없음", apiName);
                throw new CustomException(PlaceErrorCode.KAKAO_API_ERROR);
            }
            return body;
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            // 타임아웃, 연결 실패, 응답 파싱 실패
            log.warn("[KakaoLocal] {} 호출 중 오류: {}", apiName, e.getMessage());
            throw new CustomException(PlaceErrorCode.KAKAO_API_ERROR);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
