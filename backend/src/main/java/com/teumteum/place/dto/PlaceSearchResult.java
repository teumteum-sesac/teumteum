package com.teumteum.place.dto;

/**
 * 장소·주소 검색 결과 한 줄 (REQ-007 위치 설정 자동완성, REQ-052 홈 검색 공용).
 *
 * @param type              PLACE(장소) 또는 ADDRESS(주소)
 * @param kakaoPlaceId      카카오 장소 ID. 주소 결과는 null
 * @param name              표시 이름 (장소명 또는 주소)
 * @param address           지번 주소
 * @param roadAddress       도로명 주소. 없으면 null
 * @param categoryGroupCode 카카오 카테고리 그룹 코드 (CE7 등). 없으면 null
 * @param categoryName      카카오 카테고리 전체 이름 ("음식점 > 카페 > 커피전문점"). 없으면 null
 * @param latitude          위도
 * @param longitude         경도
 * @param distance          검색 기준 좌표에서의 거리(m). 기준 좌표가 없거나 주소 결과면 null
 */
public record PlaceSearchResult(
        Type type,
        String kakaoPlaceId,
        String name,
        String address,
        String roadAddress,
        String categoryGroupCode,
        String categoryName,
        double latitude,
        double longitude,
        Integer distance
) {
    public enum Type {
        PLACE,
        ADDRESS
    }
}
