package com.teumteum.recommend.dto;

import com.teumteum.global.common.category.Category;

/**
 * 장소 교체 후보 1곳 (DET-03 목록의 한 줄, "당구장 H · 도보 20분 · 총 시간 +5분").
 * <pre>
 * {
 *   "placeId": "201", "placeName": "당구장 F", "category": "SPORTS", "subCategory": "당구",
 *   "address": "서울 성동구 ○○로 5", "latitude": 37.5455, "longitude": 127.0510,
 *   "imageUrl": null, "stayMinutes": 90,
 *   "travelMinutes": 12, "transitInfo": null,
 *   "diffMinutes": -3, "exceedsAvailableTime": false, "estimated": true
 * }
 * </pre>
 * exceedsAvailableTime 이 true 면 프론트는 회색(선택 불가)으로 표시하고, 서버도 교체 요청 시 409 로 막는다.
 * 후보 목록의 이동시간은 직선거리 추정이라(이동시간 API 호출 안 함) estimated 는 지금 항상 true,
 * transitInfo 는 항상 null 이다. 프론트는 "약 12분" 처럼 예상 시간으로 표시한다 (NFR-025).
 * (docs/recommend-api.md 4)
 */
public record AlternativePlaceResponse(
        String placeId,                  // 장소 ID
        String placeName,                // 장소명
        Category category,           // 대분류 코드
        String subCategory,              // 세분류 이름
        String address,                  // 주소, 없으면 null
        double latitude,                 // 위도
        double longitude,                // 경도
        String imageUrl,                 // 썸네일, 없으면 null
        int stayMinutes,                 // 예상 체류시간(분)
        int travelMinutes,               // 직전 지점 → 이 후보 이동시간(분)
        String transitInfo,              // 대중교통 노선·환승, 아니면 null
        int diffMinutes,                 // 이 후보로 바꿨을 때 총 소요시간 변화(분). 음수면 줄어듦
        boolean exceedsAvailableTime,    // 바꾸면 이용 시간을 넘는지
        boolean estimated                // 이동시간이 직선거리 추정이면 true (교체 확정 때 실제 API 로 다시 계산)
) {
}
