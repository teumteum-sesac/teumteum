package com.teumteum.place.dto.tour;

/**
 * TourAPI 소개정보 조회(detailIntro2) 결과. 관광타입마다 필드 이름이 달라 클라이언트에서 맞춰 넣는다.
 *
 * @param openingHours 이용·영업 시간. 없으면 null
 * @param restDate     쉬는 날. 없으면 null
 */
public record TourIntro(String openingHours, String restDate) {
}
