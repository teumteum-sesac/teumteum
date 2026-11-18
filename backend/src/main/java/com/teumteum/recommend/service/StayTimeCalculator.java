package com.teumteum.recommend.service;

import com.teumteum.global.common.category.SubCategory;
import org.springframework.stereotype.Component;

@Component
public class StayTimeCalculator {

    // 세분류를 못 찾았을 때 쓰는 기본 체류시간(분)
    static final int DEFAULT_STAY_MINUTES = 40;

    public int calculate(String subCategoryName) {
        return SubCategory.fromDisplayName(subCategoryName)
                .map(SubCategory::getStayMinutes)
                .orElse(DEFAULT_STAY_MINUTES);
    }
}
