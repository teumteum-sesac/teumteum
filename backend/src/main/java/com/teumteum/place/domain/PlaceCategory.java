package com.teumteum.place.domain;

/**
 * 대분류 코드. DB category.category_code, 프론트 shared/types/domain.ts 와 같다.
 */
public enum PlaceCategory {
    CULTURE("문화생활"),
    CAFE("카페/디저트"),
    FOOD("음식/맛집"),
    NATURE("자연/휴식"),
    ENTERTAINMENT("오락/체험"),
    SHOPPING("쇼핑/구경"),
    SPORTS("스포츠활동"),
    TOURISM("관광/명소");

    private final String displayName;

    PlaceCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}