package com.teumteum.place.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.kakao.KakaoAddressResponse;
import com.teumteum.place.dto.kakao.KakaoCoord2AddressResponse;
import com.teumteum.place.dto.kakao.KakaoPlaceResponse;
import com.teumteum.place.dto.AddressResponse;
import com.teumteum.place.dto.PlaceSearchResult;
import com.teumteum.place.exception.PlaceErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 장소·주소 검색 (REQ-007, REQ-052) 과 좌표→주소 변환.
 */
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    /** 주소 결과는 앞쪽에 최대 몇 개만 보여준다 */
    private static final int ADDRESS_RESULT_SIZE = 5;

    private final KakaoLocalClient kakaoLocalClient;

    /**
     * 키워드 검색과 주소 검색을 함께 호출해 합친다.
     * 주소 결과(주소를 입력했을 때만 나옴)를 앞에, 장소 결과를 뒤에 둔다.
     * 둘 중 하나만 실패하면 나머지 결과로 응답하고, 둘 다 실패할 때만 오류를 낸다.
     * 결과가 없으면 오류가 아니라 빈 목록을 돌려준다 (자동완성 중에는 결과 없음이 정상).
     */
    public List<PlaceSearchResult> search(String query, Double lat, Double lng) {
        validateCoordinatePair(lat, lng);
        String keyword = query.trim();

        List<PlaceSearchResult> addresses = List.of();
        List<PlaceSearchResult> places = List.of();
        CustomException addressFailure = null;
        CustomException placeFailure = null;

        try {
            addresses = toAddressResults(kakaoLocalClient.searchAddress(keyword, 1, ADDRESS_RESULT_SIZE));
        } catch (CustomException e) {
            addressFailure = e;
        }
        try {
            places = toPlaceResults(kakaoLocalClient.searchKeyword(
                    keyword, lng, lat, 1, KakaoLocalClient.PLACE_MAX_SIZE));
        } catch (CustomException e) {
            placeFailure = e;
        }

        if (addressFailure != null && placeFailure != null) {
            throw placeFailure;
        }

        List<PlaceSearchResult> results = new ArrayList<>(addresses.size() + places.size());
        results.addAll(addresses);
        results.addAll(places);
        return results;
    }

    /**
     * 좌표를 주소로 바꾼다. 주소가 없는 좌표면 모든 값이 null 인 응답을 돌려준다.
     */
    public AddressResponse getAddress(double lat, double lng) {
        KakaoCoord2AddressResponse response = kakaoLocalClient.coordToAddress(lng, lat);
        return response.documentsOrEmpty().stream()
                .findFirst()
                .map(doc -> {
                    String road = doc.roadAddress() != null ? emptyToNull(doc.roadAddress().addressName()) : null;
                    String building = doc.roadAddress() != null ? emptyToNull(doc.roadAddress().buildingName()) : null;
                    String jibun = doc.address() != null ? emptyToNull(doc.address().addressName()) : null;
                    String display = building != null ? building : (road != null ? road : jibun);
                    return new AddressResponse(display, road, jibun);
                })
                .orElse(new AddressResponse(null, null, null));
    }

    private void validateCoordinatePair(Double lat, Double lng) {
        if ((lat == null) != (lng == null)) {
            throw new CustomException(PlaceErrorCode.INVALID_COORDINATE);
        }
    }

    private List<PlaceSearchResult> toPlaceResults(KakaoPlaceResponse response) {
        List<PlaceSearchResult> results = new ArrayList<>();
        for (KakaoPlaceResponse.Document doc : response.documentsOrEmpty()) {
            Optional<Double> lat = parseDouble(doc.y());
            Optional<Double> lng = parseDouble(doc.x());
            if (lat.isEmpty() || lng.isEmpty()) {
                continue; // 좌표 없는 장소는 제외 (NFR-022)
            }
            results.add(new PlaceSearchResult(
                    PlaceSearchResult.Type.PLACE,
                    doc.id(),
                    doc.placeName(),
                    emptyToNull(doc.addressName()),
                    emptyToNull(doc.roadAddressName()),
                    emptyToNull(doc.categoryGroupCode()),
                    emptyToNull(doc.categoryName()),
                    lat.get(),
                    lng.get(),
                    parseInteger(doc.distance()).orElse(null)
            ));
        }
        return results;
    }

    private List<PlaceSearchResult> toAddressResults(KakaoAddressResponse response) {
        List<PlaceSearchResult> results = new ArrayList<>();
        for (KakaoAddressResponse.Document doc : response.documentsOrEmpty()) {
            Optional<Double> lat = parseDouble(doc.y());
            Optional<Double> lng = parseDouble(doc.x());
            if (lat.isEmpty() || lng.isEmpty()) {
                continue;
            }
            String road = doc.roadAddress() != null ? emptyToNull(doc.roadAddress().addressName()) : null;
            String jibun = doc.address() != null ? emptyToNull(doc.address().addressName()) : null;
            String name = road != null ? road : (jibun != null ? jibun : doc.addressName());
            results.add(new PlaceSearchResult(
                    PlaceSearchResult.Type.ADDRESS,
                    null,
                    name,
                    jibun,
                    road,
                    null,
                    null,
                    lat.get(),
                    lng.get(),
                    null
            ));
        }
        return results;
    }

    private static Optional<Double> parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
