package com.teumteum.recommend.dto;

import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.TransportType;

import java.util.List;

/**
 * 서버가 실제로 적용한 추천 조건. RES-01 상단 조건 요약 칩과 '조건 바꾸기'의 값 유지에 쓴다.
 * <pre>
 * {
 *   "origin": { LocationResponse }, "destination": null,
 *   "startTime": "13:00", "availableMinutes": 180,
 *   "transportType": "TRANSIT", "categoryMode": "DIRECT", "categories": ["SPORTS", "SHOPPING"]
 * }
 * </pre>
 * 요청(RecommendRequest)과 필드가 같지만 "실제로 적용한 값"이라 다를 수 있다.
 * 예) DIRECT 인데 categories 가 비어 있었으면 RANDOM 으로 처리하고 categoryMode 를 RANDOM 으로 돌려준다.
 * (docs/recommend-api.md 1.3)
 */
public record RecommendConditionResponse(
        LocationResponse origin,           // 출발지
        LocationResponse destination,      // 도착지, 없으면 null
        String startTime,                  // 시작 시각 "HH:mm"
        int availableMinutes,              // 이용 시간(분)
        TransportType transportType,       // 이동수단
        CategoryMode categoryMode,         // 실제 적용한 카테고리 선택 방식
        List<CategoryCode> categories      // 실제 적용한 대분류 (중복 제거 후). RANDOM 이면 빈 목록
) {
}
