package com.teumteum.place.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.teumteum.place.dto.tour.TourCommon;
import com.teumteum.place.dto.tour.TourIntro;
import com.teumteum.place.dto.tour.TourPlace;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

/**
 * 한국관광공사 TourAPI(KorService2) 호출 전담 클라이언트 (NFR-029).
 * 서비스 키는 커밋되지 않는 application-local.yml 의 tour.service-key 에서 읽는다 (NFR-005).
 *
 * 장소 상세는 TourAPI 가 없어도 카카오맵 링크로 응답해야 하므로 (REQ-017, NFR-027),
 * 이 클라이언트는 예외를 던지지 않는다. 키가 없거나, 호출이 실패하거나, 결과가 없으면 빈 값을 돌려준다.
 * 좌표 주의: TourAPI 도 mapX = 경도, mapY = 위도 이다.
 */
@Slf4j
@Component
public class TourApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_ROWS = 100;

    /** 관광타입별 소개정보 필드 이름: { 이용시간, 쉬는 날 } */
    private static final Map<String, String[]> INTRO_FIELDS = Map.of(
            "12", new String[]{"usetime", "restdate"},
            "14", new String[]{"usetimeculture", "restdateculture"},
            "15", new String[]{"playtime", null},
            "28", new String[]{"usetimeleports", "restdateleports"},
            "38", new String[]{"opentime", "restdateshopping"},
            "39", new String[]{"opentimefood", "restdatefood"}
    );

    private final String serviceKey;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TourApiClient(@Value("${tour.service-key:}") String serviceKey, ObjectMapper objectMapper) {
        this.serviceKey = serviceKey;
        this.objectMapper = objectMapper;
        if (!StringUtils.hasText(serviceKey)) {
            // 서버는 켜지게 두고 장소 상세만 카카오맵 링크로 응답한다 (키 없는 팀원도 bootRun 가능)
            log.warn("[TourAPI] tour.service-key 가 비어 있어 장소 상세의 사진·소개·운영시간을 제공하지 않습니다.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        // 서비스 키에 + / = 가 있어도 깨지지 않게, URI 변수 값은 엄격하게 인코딩한다
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(BASE_URL);
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        this.restClient = RestClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .requestFactory(requestFactory)
                .build();
    }

    /** 좌표 반경 안의 TourAPI 장소를 가까운 순으로 돌려준다 */
    public List<TourPlace> findNearby(double lng, double lat, int radius) {
        return call("locationBasedList2", uri -> withCommonParams(uri.path("/locationBasedList2"))
                .queryParam("mapX", lng)
                .queryParam("mapY", lat)
                .queryParam("radius", radius)
                .queryParam("arrange", "E")
                .queryParam("numOfRows", MAX_ROWS)
                .queryParam("pageNo", 1)
                .build(Map.of("serviceKey", serviceKey)))
                .stream()
                .map(TourApiClient::toTourPlace)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 소개(overview)와 대표 이미지 */
    public Optional<TourCommon> findCommon(String contentId) {
        return call("detailCommon2", uri -> withCommonParams(uri.path("/detailCommon2"))
                .queryParam("contentId", contentId)
                .build(Map.of("serviceKey", serviceKey)))
                .stream()
                .findFirst()
                .map(item -> new TourCommon(clean(text(item, "overview")), text(item, "firstimage")));
    }

    /** 이용시간과 쉬는 날. 관광타입마다 필드 이름이 다르다 */
    public Optional<TourIntro> findIntro(String contentId, String contentTypeId) {
        String[] fields = INTRO_FIELDS.get(contentTypeId);
        if (fields == null) {
            return Optional.empty();
        }
        return call("detailIntro2", uri -> withCommonParams(uri.path("/detailIntro2"))
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId)
                .build(Map.of("serviceKey", serviceKey)))
                .stream()
                .findFirst()
                .map(item -> new TourIntro(
                        clean(text(item, fields[0])),
                        fields[1] == null ? null : clean(text(item, fields[1]))));
    }

    private UriBuilder withCommonParams(UriBuilder uri) {
        return uri.queryParam("serviceKey", "{serviceKey}")
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "teumteum")
                .queryParam("_type", "json");
    }

    /** 호출 후 response.body.items.item 을 목록으로 꺼낸다. 실패하면 빈 목록 */
    private List<JsonNode> call(String apiName, Function<UriBuilder, URI> uriFunction) {
        if (!StringUtils.hasText(serviceKey)) {
            return List.of();
        }
        try {
            String body = restClient.get().uri(uriFunction).retrieve().body(String.class);
            if (body == null) {
                log.warn("[TourAPI] {} 응답 본문 없음", apiName);
                return List.of();
            }
            // 키 오류 등은 _type=json 이어도 XML 로 올 수 있어 파싱 실패 → 빈 목록
            JsonNode response = objectMapper.readTree(body).path("response");
            String resultCode = response.path("header").path("resultCode").asString("");
            if (!"0000".equals(resultCode)) {
                log.warn("[TourAPI] {} 오류 응답 resultCode={}", apiName, resultCode);
                return List.of();
            }
            // 결과가 없으면 items 가 "" 로, 한 건이면 item 이 배열이 아닌 객체로 올 수 있다
            JsonNode item = response.path("body").path("items").path("item");
            if (item.isArray()) {
                List<JsonNode> items = new ArrayList<>();
                item.forEach(items::add);
                return items;
            }
            return item.isObject() ? List.of(item) : List.of();
        } catch (Exception e) {
            // 타임아웃, 연결 실패, 4xx/5xx, 파싱 실패
            log.warn("[TourAPI] {} 호출 실패: {}", apiName, e.getMessage());
            return List.of();
        }
    }

    private static TourPlace toTourPlace(JsonNode item) {
        String contentId = text(item, "contentid");
        String title = text(item, "title");
        if (contentId == null || title == null) {
            return null;
        }
        return new TourPlace(
                contentId,
                text(item, "contenttypeid"),
                title,
                parseDouble(text(item, "dist")),
                text(item, "firstimage"),
                text(item, "cpyrhtDivCd"));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** TourAPI 소개·시간 값에 섞인 HTML 태그를 정리한다 */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static Double parseDouble(String value) {
        try {
            return value == null ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}