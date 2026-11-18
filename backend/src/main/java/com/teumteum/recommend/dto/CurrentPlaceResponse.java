package com.teumteum.recommend.dto;

import com.teumteum.global.common.category.Category;

/**
 * 장소 교체 화면(DET-03) 위쪽의 "지금: 당구장 A" — 바꾸려는 현재 장소의 요약.
 * <pre>
 * { "placeId": "101", "visitOrder": 1, "placeName": "당구장 A", "category": "SPORTS", "subCategory": "당구", "stayMinutes": 90 }
 * </pre>
 * (docs/recommend-api.md 4)
 */
public record CurrentPlaceResponse(
        String placeId,           // 장소 ID
        int visitOrder,           // 코스 안 방문 순서
        String placeName,         // 장소명
        Category category,    // 대분류 코드
        String subCategory,       // 세분류 이름
        int stayMinutes           // 예상 체류시간(분)
) {
}
