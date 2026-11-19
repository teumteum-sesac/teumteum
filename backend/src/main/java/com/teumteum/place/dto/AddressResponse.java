package com.teumteum.place.dto;

/**
 * 좌표→주소 변환 결과 (현재 위치, 지도 핀 위치 표시용).
 * 주소를 찾지 못하면 모든 값이 null 이다. 화면에서 "현재 위치" 등으로 대신 표시한다 (NFR-026: 정보를 지어내지 않음).
 *
 * @param displayName  표시용 이름 (건물명 > 도로명 주소 > 지번 주소 순)
 * @param roadAddress  도로명 주소
 * @param jibunAddress 지번 주소
 */
public record AddressResponse(String displayName, String roadAddress, String jibunAddress) {
}
