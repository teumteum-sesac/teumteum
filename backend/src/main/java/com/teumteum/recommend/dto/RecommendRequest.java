package com.teumteum.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.TransportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 추천 생성 요청 (POST /api/recommendations). 사용자가 CON-01 에서 고른 외출 조건 전체.
 * <pre>
 * {
 *   "origin": { "name": "성수역", "address": null, "latitude": 37.5446, "longitude": 127.0559 },
 *   "destination": null,
 *   "startTime": "13:00",
 *   "availableMinutes": 180,
 *   "transportType": "TRANSIT",
 *   "categoryMode": "DIRECT",
 *   "categories": ["SPORTS", "SHOPPING"]
 * }
 * </pre>
 * - origin           : 출발지, 필수. @Valid 로 안쪽 위도·경도까지 검사
 * - destination      : 도착지, 선택. 없으면 출발지 주변에서 끝나는 코스
 * - startTime        : 시작 시각 "HH:mm", 30분 단위 (분은 00 또는 30)
 * - availableMinutes : 이용 시간(분) 30 ~ 360. 0분은 추천 불가
 * - transportType    : 이동수단 TRANSIT / WALK / CAR
 * - categoryMode     : 카테고리 선택 방식 DIRECT / RANDOM
 * - categories       : 대분류 0 ~ 3개. RANDOM 이면 무시, DIRECT 인데 비어 있으면 RANDOM 으로 처리 (서비스에서)
 * 검증 실패는 컨트롤러의 @Valid 에서 걸려 400 INVALID_REQUEST 가 된다. (docs/recommend-api.md 1.2)
 */
@Schema(description = "추천 생성 요청 (외출 조건)")
public record RecommendRequest(

        @Schema(description = "출발지 (필수)")
        @NotNull
        @Valid
        LocationRequest origin,

        @Schema(description = "도착지 (선택). 비우면 출발지 주변에서 끝나는 코스", nullable = true)
        @Valid
        LocationRequest destination,

        @Schema(description = "시작 시각 HH:mm, 30분 단위", example = "14:00")
        @NotNull
        @Pattern(regexp = "^([01]\\d|2[0-3]):(00|30)$")
        String startTime,

        @Schema(description = "이용 시간(분) 30~360. 1시간 이하 1곳 · 3시간까지 2곳 · 5시간까지 3곳 · 그 이상 4곳", example = "180")
        @NotNull
        @Min(30)
        @Max(360)
        Integer availableMinutes,

        @Schema(description = "이동수단 TRANSIT 대중교통 / WALK 도보 / CAR 차량", example = "WALK")
        @NotNull
        TransportType transportType,

        @Schema(description = "카테고리 방식 DIRECT 직접 선택 / RANDOM 랜덤", example = "DIRECT")
        @NotNull
        CategoryMode categoryMode,

        @Schema(description = "대분류 0~3개 (DIRECT 일 때). 비우면 랜덤. CULTURE·CAFE·FOOD·NATURE·ENTERTAINMENT·SHOPPING·SPORTS·TOURISM", example = "[\"CAFE\", \"FOOD\"]")
        @Size(max = 3)
        List<Category> categories
) {
}
