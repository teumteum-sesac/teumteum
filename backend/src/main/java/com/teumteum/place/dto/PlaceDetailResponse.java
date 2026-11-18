package com.teumteum.place.dto;

/**
 * 장소 상세 (REQ-017). 장소명·주소·카테고리·좌표는 프론트가 목록에서 이미 가지고 있어 다시 보내지 않는다.
 * TourAPI 에 없는 장소는 tourInfoAvailable = false 이고, 카카오맵 링크만 채운다 (확인되지 않은 정보는 만들지 않음, NFR-026).
 *
 * @param kakaoPlaceId       카카오 장소 ID
 * @param tourInfoAvailable  TourAPI 정보를 찾았는지
 * @param imageUrl           사진. 없으면 null
 * @param imageCopyrightType 사진 저작권 유형 (공공누리 Type1, Type3 등). 사진이 있으면 출처(한국관광공사) 표시 필요
 * @param overview           소개. 없으면 null
 * @param openingHours       이용·영업 시간. 없으면 null
 * @param restDate           쉬는 날. 없으면 null
 * @param kakaoMapUrl        카카오맵 장소 링크 (항상 있음)
 */
public record PlaceDetailResponse(
        String kakaoPlaceId,
        boolean tourInfoAvailable,
        String imageUrl,
        String imageCopyrightType,
        String overview,
        String openingHours,
        String restDate,
        String kakaoMapUrl
) {

    public static PlaceDetailResponse linkOnly(String kakaoPlaceId, String kakaoMapUrl) {
        return new PlaceDetailResponse(kakaoPlaceId, false, null, null, null, null, null, kakaoMapUrl);
    }
}
