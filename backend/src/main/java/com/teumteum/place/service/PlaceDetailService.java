package com.teumteum.place.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.PlaceDetailResponse;
import com.teumteum.place.dto.tour.TourCommon;
import com.teumteum.place.dto.tour.TourIntro;
import com.teumteum.place.dto.tour.TourPlace;
import com.teumteum.place.exception.PlaceErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 상세 (REQ-017). 주변 탐색·홈 검색에서 오는 장소는 DB 에 없을 수 있어,
 * 카카오 장소의 이름·좌표로 TourAPI 를 찾아 사진·소개·운영시간을 붙인다.
 * TourAPI 에 없거나 호출이 실패하면 카카오맵 링크만 돌려준다 (오류 아님, NFR-027).
 */
@Service
@RequiredArgsConstructor
public class PlaceDetailService {

    /** TourAPI 를 찾을 반경(m). 궁궐·공원처럼 넓은 곳은 두 서비스의 기준점이 수백 m 다를 수 있다 */
    static final int MATCH_RADIUS = 1_000;
    static final String KAKAO_MAP_URL = "https://place.map.kakao.com/";

    private final TourApiClient tourApiClient;

    public PlaceDetailResponse getDetail(String kakaoPlaceId, String name, double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new CustomException(PlaceErrorCode.INVALID_COORDINATE);
        }
        String kakaoMapUrl = KAKAO_MAP_URL + kakaoPlaceId;

        Optional<TourPlace> match = TourPlaceMatcher.findBest(name, tourApiClient.findNearby(lng, lat, MATCH_RADIUS));
        if (match.isEmpty()) {
            return PlaceDetailResponse.linkOnly(kakaoPlaceId, kakaoMapUrl);
        }

        TourPlace place = match.get();
        TourCommon common = tourApiClient.findCommon(place.contentId()).orElse(null);
        TourIntro intro = tourApiClient.findIntro(place.contentId(), place.contentTypeId()).orElse(null);

        String imageUrl = place.firstImage() != null ? place.firstImage()
                : common != null ? common.firstImage() : null;

        return new PlaceDetailResponse(
                kakaoPlaceId,
                true,
                imageUrl,
                imageUrl != null ? place.copyrightType() : null,
                common != null ? common.overview() : null,
                intro != null ? intro.openingHours() : null,
                intro != null ? intro.restDate() : null,
                kakaoMapUrl);
    }
}
