package com.teumteum.place.dto;

import com.teumteum.global.common.category.Category;

/**
 * 주변 장소 후보 (REQ-015·016).
 *
 * @param kakaoPlaceId 카카오 장소 ID (place.external_place_id, 중복 판단 기준 NFR-010)
 * @param name         장소명
 * @param category     대분류 코드
 * @param subCategory  세분류 이름 (DB category.category_name)
 * @param address      지번 주소
 * @param roadAddress  도로명 주소. 없으면 null
 * @param latitude     위도
 * @param longitude    경도
 * @param distance     출발 위치에서의 거리(m). 모르면 null
 * @param placeUrl     카카오맵 장소 링크
 */
public record NearbyPlaceResult(
        String kakaoPlaceId,
        String name,
        Category category,
        String subCategory,
        String address,
        String roadAddress,
        double latitude,
        double longitude,
        Integer distance,
        String placeUrl
) {
}