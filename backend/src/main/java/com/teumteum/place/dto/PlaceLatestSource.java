package com.teumteum.place.dto;

// 외부 검색에 필요한 연결 정보. 이름·좌표는 검색 단서이며 최신 결과로 반환하지 않는다.
public record PlaceLatestSource(String externalPlaceId, String name, double latitude, double longitude) {
}
