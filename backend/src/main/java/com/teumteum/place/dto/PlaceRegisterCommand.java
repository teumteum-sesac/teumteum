package com.teumteum.place.dto;

import com.teumteum.place.domain.PlaceCategory;

/**
 * 장소 조회·등록(findOrCreate) 입력. 추천 결과에 이미 있는 장소 정보를 그대로 넘긴다 (외부 API 재호출 없음).
 *
 * @param kakaoPlaceId    카카오 장소 ID (place.external_place_id)
 * @param name            장소명
 * @param category        대분류
 * @param subCategoryName 세분류 이름 (category.category_name, 예: "카페")
 * @param address         주소. 도로명이 있으면 도로명, 없으면 지번. 없으면 null
 * @param latitude        위도
 * @param longitude       경도
 */
public record PlaceRegisterCommand(
        String kakaoPlaceId,
        String name,
        PlaceCategory category,
        String subCategoryName,
        String address,
        double latitude,
        double longitude
) {
}
