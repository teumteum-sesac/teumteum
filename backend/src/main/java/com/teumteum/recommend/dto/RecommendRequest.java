package com.teumteum.recommend.dto;

import com.teumteum.recommend.domain.CategoryCode;
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
public record RecommendRequest(

        @NotNull
        @Valid
        LocationRequest origin,

        @Valid
        LocationRequest destination,

        @NotNull
        @Pattern(regexp = "^([01]\\d|2[0-3]):(00|30)$")
        String startTime,

        @NotNull
        @Min(30)
        @Max(360)
        Integer availableMinutes,

        @NotNull
        TransportType transportType,

        @NotNull
        CategoryMode categoryMode,

        @Size(max = 3)
        List<CategoryCode> categories
) {
}
