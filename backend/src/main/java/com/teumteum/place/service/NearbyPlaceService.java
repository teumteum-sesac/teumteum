package com.teumteum.place.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.common.category.Category;
import com.teumteum.place.domain.PlaceSearchSource;
import com.teumteum.global.common.category.SubCategory;
import com.teumteum.place.domain.SubCategorySearchRule;
import com.teumteum.place.dto.NearbyPlaceResult;
import com.teumteum.place.dto.kakao.KakaoPlaceResponse;
import com.teumteum.place.exception.PlaceErrorCode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 주변 장소 후보 검색 (REQ-015), 카테고리 필터링 (REQ-016), 주변 탐색 (REQ-051).
 *
 * 반경 확대는 이 서비스가 판단하지 않는다. REQ-015 는 "맞는 코스가 없으면" 넓히므로,
 * 추천 서비스가 코스 구성에 실패하면 RADIUS_STEPS 의 다음 반경으로 다시 호출한다.
 * 주변 탐색 (REQ-051) 은 사용자가 고른 반경(500m / 1km / 3km)으로 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

    /** 추천 서비스가 코스 구성에 실패했을 때 차례로 넓혀 쓸 반경(m). TODO: 단계 확정 (be03) */
    public static final List<Integer> RADIUS_STEPS = List.of(1_000, 2_000, 3_000, 5_000);
    /** 카카오 로컬 반경 최대값(m) */
    public static final int MAX_RADIUS = 20_000;
    public static final int MAX_CATEGORIES = 3;

    private final KakaoLocalClient kakaoLocalClient;

    /**
     * 출발 좌표 반경 안에서 선택한 대분류의 장소 후보를 가까운 순으로 돌려준다.
     *
     * @param categories 1~3개. 비어 있거나 null 이면 랜덤 추천으로 보고 대분류 구분 없이 조회한다 (REQ-009, REQ-016)
     * @param radius     검색 반경(m), 1 ~ 20,000
     * @return 후보 목록. 결과가 없으면 빈 목록 (오류 아님). 모든 카카오 호출이 실패할 때만 예외
     */
    public List<NearbyPlaceResult> findCandidates(double lat, double lng, Set<Category> categories, int radius) {
        validate(lat, lng, categories, radius);
        Set<Category> targets = (categories == null || categories.isEmpty())
                ? EnumSet.allOf(Category.class)
                : categories;
        return searchAll(SubCategorySearchRule.sourcesOf(targets), lat, lng, radius, targets);
    }

    private List<NearbyPlaceResult> searchAll(Set<PlaceSearchSource> sources, double lat, double lng,
                                              int radius, Set<Category> targets) {
        Map<String, NearbyPlaceResult> byId = new LinkedHashMap<>();
        CustomException lastFailure = null;
        int failures = 0;

        for (PlaceSearchSource source : sources) {
            try {
                KakaoPlaceResponse response = search(source, lat, lng, radius);
                for (KakaoPlaceResponse.Document doc : response.documentsOrEmpty()) {
                    toResult(doc, targets).ifPresent(r -> byId.putIfAbsent(r.kakaoPlaceId(), r));
                }
            } catch (CustomException e) {
                failures++;
                lastFailure = e;
                log.warn("[NearbyPlace] 검색 실패 source={} radius={}m", source, radius);
            }
        }

        if (failures == sources.size() && lastFailure != null) {
            throw lastFailure;
        }
        return byId.values().stream()
                .sorted(Comparator.comparing(NearbyPlaceResult::distance,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private KakaoPlaceResponse search(PlaceSearchSource source, double lat, double lng, int radius) {
        if (source.isGroup()) {
            return kakaoLocalClient.searchCategory(source.group(), lng, lat, radius, 1,
                    KakaoLocalClient.PLACE_MAX_SIZE);
        }
        return kakaoLocalClient.searchKeyword(source.keyword(), lng, lat, radius, 1,
                KakaoLocalClient.PLACE_MAX_SIZE);
    }

    /** 세분류가 정해지지 않거나, 대상 대분류가 아니거나, 좌표가 없으면 제외 */
    private Optional<NearbyPlaceResult> toResult(KakaoPlaceResponse.Document doc, Set<Category> targets) {
        if (!StringUtils.hasText(doc.id())) {
            return Optional.empty();
        }
        Optional<SubCategory> sub = SubCategorySearchRule.classify(doc.categoryName());
        if (sub.isEmpty() || !targets.contains(sub.get().getCategory())) {
            return Optional.empty();
        }
        Double lat = parseDouble(doc.y());
        Double lng = parseDouble(doc.x());
        if (lat == null || lng == null) {
            return Optional.empty(); // NFR-022
        }
        return Optional.of(new NearbyPlaceResult(
                doc.id(),
                doc.placeName(),
                sub.get().getCategory(),
                sub.get().getDisplayName(),
                doc.addressName(),
                StringUtils.hasText(doc.roadAddressName()) ? doc.roadAddressName() : null,
                lat,
                lng,
                parseInteger(doc.distance()),
                doc.placeUrl()
        ));
    }

    private void validate(double lat, double lng, Set<Category> categories, int radius) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new CustomException(PlaceErrorCode.INVALID_COORDINATE);
        }
        if (categories != null && categories.size() > MAX_CATEGORIES) {
            throw new CustomException(PlaceErrorCode.INVALID_CATEGORY);
        }
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new CustomException(PlaceErrorCode.INVALID_RADIUS);
        }
    }

    private static Double parseDouble(String value) {
        try {
            return StringUtils.hasText(value) ? Double.parseDouble(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return StringUtils.hasText(value) ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}