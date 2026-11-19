package com.teumteum.place.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.domain.Place;
import com.teumteum.place.dto.PlaceDetailResponse;
import com.teumteum.place.dto.tour.TourCommon;
import com.teumteum.place.dto.tour.TourIntro;
import com.teumteum.place.dto.tour.TourPlace;
import com.teumteum.place.exception.PlaceErrorCode;
import com.teumteum.place.repository.PlaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 상세 (REQ-017). 주변 탐색·홈 검색에서 오는 장소는 DB 에 없을 수 있어,
 * 카카오 장소의 이름·좌표로 TourAPI 를 찾아 사진·소개·운영시간을 붙인다.
 * TourAPI 에 없거나 호출이 실패하면 카카오맵 링크만 돌려준다 (오류 아님, NFR-027).
 * 저장한 코스의 장소처럼 우리 장소 ID 만 아는 경우는 {@link #getDetailByPlaceId(Long)} 로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceDetailService {

    /** TourAPI 를 찾을 반경(m). 궁궐·공원처럼 넓은 곳은 두 서비스의 기준점이 수백 m 다를 수 있다 */
    static final int MATCH_RADIUS = 1_000;
    static final String KAKAO_MAP_URL = "https://place.map.kakao.com/";

    private final TourApiClient tourApiClient;
    private final PlaceRepository placeRepository;

    /**
     * 우리 장소 ID(place 테이블)로 상세를 조회한다. 저장한 코스의 placeId 는 카카오 ID 가 아니라 이 값이다.
     * 저장해 둔 카카오 장소 ID·이름·좌표로 {@link #getDetail} 과 같은 방식으로 TourAPI 를 찾는다.
     */
    @Transactional(readOnly = true)
    public PlaceDetailResponse getDetailByPlaceId(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));
        return getDetail(place.getExternalPlaceId(), place.getName(),
                place.getLatitude().doubleValue(), place.getLongitude().doubleValue());
    }

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
