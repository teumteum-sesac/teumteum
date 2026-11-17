package com.teumteum.place.domain;

import com.teumteum.place.service.KakaoCategory;

/**
 * 카카오에서 후보를 가져오는 방법. 카테고리 그룹 코드 검색 또는 키워드 검색 중 하나.
 */
public record PlaceSearchSource(KakaoCategory group, String keyword) {

    public static PlaceSearchSource group(KakaoCategory group) {
        return new PlaceSearchSource(group, null);
    }

    public static PlaceSearchSource keyword(String keyword) {
        return new PlaceSearchSource(null, keyword);
    }

    public boolean isGroup() {
        return group != null;
    }
}