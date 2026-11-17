package com.teumteum.recommend.domain;

/**
 * 코스에 넣을 수 있는 후보 장소 1곳 (추천 쪽 모양).
 * <p>
 * 장소 도메인(NearbyPlaceService)의 결과를 그대로 쓰지 않고 이 모양으로 바꿔서 쓴다.
 * TravelRoute 와 같은 이유다: 장소 쪽 응답 모양이 바뀌어도 변환하는 한 곳만 고치면 되고,
 * 코스 조합 로직(CourseComposer)은 장소 도메인을 몰라도 된다. 테스트에서 가짜 후보를 만들기도 쉽다.
 *
 * @param placeId     장소 ID (카카오 장소 ID). 같은 장소인지 판단하는 기준
 * @param placeName   장소명
 * @param category    대분류 (직접 선택일 때 카테고리를 맞추는 기준)
 * @param subCategory 세분류 이름 (예: "당구"). 체류시간을 정하고, 한 코스에 같은 세분류가 겹치지 않게 하는 기준
 * @param address     주소 (도로명 우선, 없으면 지번)
 * @param latitude    위도
 * @param longitude   경도
 * @param placeUrl    카카오맵 장소 링크 (코스 상세의 '카카오맵에서 보기'). 없으면 null
 */
public record PlaceCandidate(
        String placeId,
        String placeName,
        CategoryCode category,
        String subCategory,
        String address,
        double latitude,
        double longitude,
        String placeUrl
) {

    /**
     * 카카오맵 링크 없이 만드는 생성자 (placeUrl null).
     * TravelRoute 와 같은 방법: placeUrl 을 나중에 추가해서, 이 생성자 덕분에
     * 기존 테스트의 new PlaceCandidate(7개) 코드를 고치지 않아도 된다.
     */
    public PlaceCandidate(String placeId, String placeName, CategoryCode category, String subCategory,
                          String address, double latitude, double longitude) {
        this(placeId, placeName, category, subCategory, address, latitude, longitude, null);
    }
}
