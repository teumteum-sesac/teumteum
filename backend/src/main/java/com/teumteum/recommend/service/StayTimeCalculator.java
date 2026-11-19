package com.teumteum.recommend.service;

import com.teumteum.global.common.category.SubCategory;
import org.springframework.stereotype.Component;

/**
 * 세분류별 체류시간(분). 공통 SubCategory 의 최소~최대 값을 꺼내 준다 (#204).
 * <pre>
 * 최소 (calculate)    : 코스를 고를 때 어림하는 값. 이 값으로 넣어 봐야 장소가 잘 들어간다
 * 최대 (calculateMax) : 확정 후 남는 시간을 채울 때 늘릴 수 있는 한계 (StayTimeFiller)
 * </pre>
 * 표에 없는 세분류(또는 null)는 기본값 40~90분을 쓴다.
 */
@Component
public class StayTimeCalculator {

    // 세분류를 못 찾았을 때 쓰는 기본 체류시간(분)
    static final int DEFAULT_STAY_MINUTES = 40;
    static final int DEFAULT_MAX_STAY_MINUTES = 90;

    /** 최소 체류시간(분) */
    public int calculate(String subCategoryName) {
        return SubCategory.fromDisplayName(subCategoryName)
                .map(SubCategory::getMinStayMinutes)
                .orElse(DEFAULT_STAY_MINUTES);
    }

    /** 최대 체류시간(분) */
    public int calculateMax(String subCategoryName) {
        return SubCategory.fromDisplayName(subCategoryName)
                .map(SubCategory::getMaxStayMinutes)
                .orElse(DEFAULT_MAX_STAY_MINUTES);
    }

    /**
     * 원하는 체류시간을 이 세분류의 최소~최대 안으로 맞춘다 (장소 교체에서 사용).
     * <pre>
     * 예) 카페(40~90) 자리에 60분 → 60 / 120분 → 90 / 20분 → 40
     *     영화(150~180) 자리에 60분 → 150
     * </pre>
     * 장소를 바꿔도 원래 장소에 쓰던 시간을 최대한 이어받아, 총 소요시간이 크게 흔들리지 않게 한다.
     */
    public int fitWithin(String subCategoryName, int preferredMinutes) {
        int min = calculate(subCategoryName);
        int max = calculateMax(subCategoryName);
        return Math.max(min, Math.min(preferredMinutes, max));
    }
}
