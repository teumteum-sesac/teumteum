package com.teumteum.place.service;

/**
 * 카카오 로컬 카테고리 그룹 코드 중 서비스에서 쓰는 것 (REQ-016).
 */
public enum KakaoCategory {
    CT1("문화시설"),
    CE7("카페"),
    FD6("음식점"),
    AT4("관광명소");

    private final String description;

    KakaoCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
