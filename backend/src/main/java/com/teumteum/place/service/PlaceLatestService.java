package com.teumteum.place.service;

import com.teumteum.place.domain.Place;
import com.teumteum.place.domain.SubCategorySearchRule;
import com.teumteum.place.dto.*;
import com.teumteum.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceLatestService {
    private final PlaceRepository repository;
    private final KakaoLocalClient kakao;
    private final PlaceDetailService details;

    // DB 조회는 호출 스레드에서 먼저 끝낸다. DB 장애는 외부 조회 실패로 숨기지 않는다.
    public PlaceLatestSource source(Long internalPlaceId) {
        return repository.findById(internalPlaceId).filter(p -> Place.PROVIDER_KAKAO.equals(p.getProvider()))
                .map(p -> new PlaceLatestSource(p.getExternalPlaceId(), p.getName(),
                        p.getLatitude().doubleValue(), p.getLongitude().doubleValue()))
                .orElse(null);
    }

    // 내부 place_id → 카카오 장소 ID. 저장 코스 응답이 장소 상세(카카오 장소 정보)로 연결할 때 쓴다.
    // 카카오 장소가 아니거나 없는 ID 는 결과에 넣지 않는다.
    public Map<Long, String> kakaoPlaceIds(Collection<Long> internalPlaceIds) {
        if (internalPlaceIds == null || internalPlaceIds.isEmpty()) return Map.of();
        return repository.findAllById(internalPlaceIds).stream()
                .filter(p -> Place.PROVIDER_KAKAO.equals(p.getProvider()) && StringUtils.hasText(p.getExternalPlaceId()))
                .collect(Collectors.toMap(Place::getId, Place::getExternalPlaceId, (a, b) -> a));
    }

    // 이름·좌표는 검색 단서로만 사용한다. 동일 카카오 ID가 확인된 응답만 최신 정보로 반환한다.
    public PlaceLatestLookup lookup(PlaceLatestSource source) {
        if (source == null) return PlaceLatestLookup.notFound();
        var match = kakao.searchKeyword(source.name(), source.longitude(), source.latitude(),
                        1, KakaoLocalClient.PLACE_MAX_SIZE).documentsOrEmpty().stream()
                .filter(p -> source.externalPlaceId().equals(p.id())).findFirst();
        if (match.isEmpty()) return PlaceLatestLookup.notFound();
        var p = match.get();
        if (!StringUtils.hasText(p.placeName())) throw new IllegalStateException("장소명 없는 외부 응답");
        var category = SubCategorySearchRule.classify(p.categoryName()).orElse(null);
        Double lat = number(p.y(), 90);
        Double lng = number(p.x(), 180);
        // 한쪽만 유효한 좌표도 반환하지 않는다.
        if (lat == null || lng == null) { lat = null; lng = null; }

        PlaceDetailResponse extra = lat == null ? null : details.getDetail(p.id(), p.placeName(), lat, lng);
        String imageUrl = extra == null ? null : extra.imageUrl();
        return PlaceLatestLookup.success(new LatestPlaceResponse(p.placeName(),
                category == null ? null : category.getCategory().name(), category == null ? null : category.getDisplayName(),
                StringUtils.hasText(p.roadAddressName()) ? p.roadAddressName()
                        : StringUtils.hasText(p.addressName()) ? p.addressName() : null,
                lat, lng, imageUrl, imageUrl == null ? null : "한국관광공사 TourAPI",
                imageUrl == null ? null : extra.imageCopyrightType(),
                extra == null ? null : extra.overview(), extra == null ? null : extra.openingHours()));
    }

    private static Double number(String value, int max) {
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) && Math.abs(number) <= max ? number : null;
        } catch (NumberFormatException | NullPointerException invalid) {
            return null;
        }
    }
}
