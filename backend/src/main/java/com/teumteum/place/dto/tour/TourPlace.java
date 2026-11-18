package com.teumteum.place.dto.tour;

/**
 * TourAPI 위치 기반 조회(locationBasedList2) 결과 한 건.
 *
 * @param contentId     TourAPI 콘텐츠 ID
 * @param contentTypeId 관광타입 (12 관광지, 14 문화시설, 15 축제, 28 레포츠, 32 숙박, 38 쇼핑, 39 음식점)
 * @param title         장소명
 * @param distance      조회 기준 좌표에서의 거리(m). 모르면 null
 * @param firstImage    대표 이미지 URL. 없으면 null
 * @param copyrightType 이미지 저작권 유형 (Type1, Type3 등). 없으면 null
 */
public record TourPlace(
        String contentId,
        String contentTypeId,
        String title,
        Double distance,
        String firstImage,
        String copyrightType
) {
}
