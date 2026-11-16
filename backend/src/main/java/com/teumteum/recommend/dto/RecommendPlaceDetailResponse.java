package com.teumteum.recommend.dto;

import com.teumteum.recommend.domain.CategoryCode;

/**
 * 코스 상세(DET-01)의 장소 1곳. RecommendPlaceResponse 의 모든 필드 + 소개·카카오맵 링크.
 * <pre>
 * { ...RecommendPlaceResponse 필드..., "description": null, "kakaoMapUrl": "https://place.map.kakao.com/123" }
 * </pre>
 * record 는 상속이 안 돼서 RecommendPlaceResponse 필드를 그대로 다시 적었다.
 * (안에 RecommendPlaceResponse 를 넣으면 JSON 이 한 겹 더 감싸져서 명세 모양과 달라진다)
 * (docs/recommend-api.md 3)
 */
public record RecommendPlaceDetailResponse(
        String placeId,           // 장소 ID
        int visitOrder,           // 방문 순서, 1부터
        String placeName,         // 장소명
        CategoryCode category,    // 대분류 코드
        String subCategory,       // 세분류 이름
        String address,           // 주소, 없으면 null
        double latitude,          // 위도
        double longitude,         // 경도
        String imageUrl,          // 썸네일, 없으면 null
        String openingHours,      // 운영시간, 없으면 null
        int stayMinutes,          // 예상 체류시간(분)
        int travelMinutes,        // 직전 지점 → 이 장소 이동시간(분)
        String transitInfo,       // 대중교통 노선·환승, 아니면 null
        String description,       // 장소 소개 (일정에서 장소를 누르면 펼쳐지는 영역), 없으면 null
        String kakaoMapUrl        // 정보가 없는 장소의 '카카오맵에서 보기' 링크
) {
}
