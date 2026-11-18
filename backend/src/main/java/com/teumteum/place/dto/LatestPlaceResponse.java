package com.teumteum.place.dto;

// 이번 외부 조회에서 확인한 값만 담는다. 저장본으로 누락된 필드를 채우지 않는다.
public record LatestPlaceResponse(
        String placeName, String category, String subCategory, String address,
        Double latitude, Double longitude, String imageUrl, String imageSource,
        String imageCopyrightType, String description, String openingHours
) {
}
