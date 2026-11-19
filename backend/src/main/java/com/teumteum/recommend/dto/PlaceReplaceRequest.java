package com.teumteum.recommend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 장소 교체 요청 (POST /api/recommendations/courses/{recommendationId}/replace). DET-03 에서 후보를 고르면 보낸다.
 * <pre>
 * { "targetPlaceId": "101", "newPlaceId": "201" }
 * </pre>
 * - targetPlaceId : 바꿀 장소 ID (지금 코스에 있는 장소)
 * - newPlaceId    : 새로 넣을 장소 ID (교체 후보 API 가 돌려준 후보 중 하나)
 * 장소 ID 는 계산에 쓰는 숫자가 아니라 이름표라서 String 으로 받는다.
 * 빈 문자열("")도 막기 위해 @NotNull 대신 @NotBlank 를 쓴다.
 * "코스에 있는 장소인지", "후보 목록에 있는 장소인지"는 저장된 추천 결과를 봐야 알 수 있어 서비스에서 검사한다.
 * (docs/recommend-api.md 5. 장소 교체)
 */
public record PlaceReplaceRequest(

        // 바꿀 장소
        @NotBlank
        String targetPlaceId,

        // 새로 넣을 장소
        @NotBlank
        String newPlaceId
) {
}
