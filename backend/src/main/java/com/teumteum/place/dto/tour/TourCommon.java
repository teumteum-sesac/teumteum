package com.teumteum.place.dto.tour;

/**
 * TourAPI 공통정보 조회(detailCommon2) 결과.
 *
 * @param overview   소개. 없으면 null
 * @param firstImage 대표 이미지 URL. 없으면 null
 */
public record TourCommon(String overview, String firstImage) {
}
