package com.teumteum.recommend.dto;

import com.teumteum.global.common.category.Category;

/**
 * 추천 코스 안의 장소 1곳. RES-01 카드와 DET-01 일정의 한 줄이 된다.
 * <pre>
 * {
 *   "placeId": "101", "visitOrder": 1,
 *   "placeName": "당구장 A", "category": "SPORTS", "subCategory": "당구",
 *   "address": "서울 성동구 ○○로 1", "latitude": 37.5470, "longitude": 127.0500,
 *   "imageUrl": null, "openingHours": "10:00~24:00",
 *   "stayMinutes": 90, "travelMinutes": 15, "transitInfo": "버스 721 · 환승 0회"
 * }
 * </pre>
 * 방문 시각(도착·출발)은 응답에 넣지 않는다. 프론트가 코스의 startTime 과 이동·체류 시간으로 계산한다.
 * (docs/recommend-api.md 1.3)
 */
public record RecommendPlaceResponse(
        String placeId,           // 장소 ID (장소 도메인 기준). 계산용 숫자가 아니라 이름표라서 String
        int visitOrder,           // 방문 순서, 1부터
        String placeName,         // 장소명
        Category category,    // 대분류 코드 (JSON 에는 "SPORTS" 처럼 나간다)
        String subCategory,       // 세분류 이름 (예: "당구")
        String address,           // 주소, 없으면 null
        double latitude,          // 위도
        double longitude,         // 경도
        String imageUrl,          // 썸네일, 없으면 null → 프론트가 카테고리 기본 이미지
        String openingHours,      // 운영시간, 없으면 null
        int stayMinutes,          // 예상 체류시간(분)
        int travelMinutes,        // 직전 지점(첫 장소면 출발지) → 이 장소 이동시간(분)
        String transitInfo        // 대중교통 노선·환승 (예: "버스 721 · 환승 0회"), 대중교통이 아니면 null
) {
}
