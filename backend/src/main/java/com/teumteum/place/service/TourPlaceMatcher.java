package com.teumteum.place.service;

import com.teumteum.place.dto.tour.TourPlace;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 카카오 장소와 TourAPI 장소를 이름으로 맞춘다. 두 서비스는 공통 ID 가 없다.
 *
 * 규칙: 숙박 제외 → 이름을 정리했을 때 완전히 같은 것만 → 여러 개면 가장 가까운 것.
 * "포함"으로 비교하지 않는다. 예) "청량리할머니냉면 용두직영점" 과 "할머니냉면" 은 다른 가게다.
 */
final class TourPlaceMatcher {

    /** 틈틈 대분류에 없는 관광타입: 32 숙박 */
    private static final Set<String> EXCLUDED_TYPES = Set.of("32");

    private TourPlaceMatcher() {
    }

    static Optional<TourPlace> findBest(String kakaoName, List<TourPlace> candidates) {
        String target = normalize(kakaoName);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(c -> !EXCLUDED_TYPES.contains(c.contentTypeId()))
                .filter(c -> target.equals(normalize(c.title())))
                .min(Comparator.comparing(TourPlace::distance, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * 괄호 내용, 띄어 쓴 지점명(예: " 용두직영점"), 공백·문장부호를 지우고 소문자로 맞춘다.
     * 마지막 단어가 "백화점"이면 지점명으로 보지 않는다.
     */
    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String value = name.replaceAll("\\(.*?\\)|\\[.*?\\]", " ").trim();
        int lastSpace = value.lastIndexOf(' ');
        if (lastSpace > 0) {
            String lastWord = value.substring(lastSpace + 1);
            if (lastWord.endsWith("점") && !lastWord.endsWith("백화점")) {
                value = value.substring(0, lastSpace);
            }
        }
        return value.replaceAll("[\\s·.,'\"&-]", "").toLowerCase(Locale.ROOT);
    }
}
