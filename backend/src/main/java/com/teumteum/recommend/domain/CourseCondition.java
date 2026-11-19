package com.teumteum.recommend.domain;

import com.teumteum.global.common.category.Category;

import com.teumteum.recommend.domain.TravelRoute.Coordinate;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 코스를 만들 때 쓰는 조건 (요청 DTO 에서 코스 조합에 필요한 것만 추린 모양).
 * <p>
 * RecommendRequest(요청 DTO)를 코스 조합에 바로 넘기지 않는 이유:
 * 다시 추천은 요청 본문 없이 서버에 보관한 조건으로 코스를 만든다. 조건 모양이 따로 있으면
 * "새 요청" 이든 "보관한 조건" 이든 이 모양으로 바꿔서 같은 코스 조합 로직을 쓸 수 있다.
 *
 * @param origin           출발지 좌표 (필수)
 * @param destination      도착지 좌표. 없으면 null (돌아오는 시간은 계산하지 않음)
 * @param availableMinutes 이용 가능 시간(분), 이동 포함
 * @param transportType    이동수단
 * @param categories       직접 선택한 대분류 (최대 3개). 비어 있으면 랜덤 추천
 */
public record CourseCondition(
        Coordinate origin,
        Coordinate destination,
        int availableMinutes,
        TransportType transportType,
        List<Category> categories
) {

    /**
     * compact 생성자: record 의 필드에 값이 들어가기 직전에 실행된다. 들어온 값을 정리할 때 쓴다.
     * - null 이면 빈 목록 (랜덤)
     * - 중복은 하나로 정리 (명세 6.1 "대분류 중복은 서버에서 정리"). LinkedHashSet 은 처음 넣은 순서를 지킨다
     * - List.copyOf 로 바꿀 수 없는 목록을 만든다 → 밖에서 넘긴 목록을 나중에 고쳐도 조건은 그대로
     */
    public CourseCondition {
        categories = (categories == null) ? List.of() : List.copyOf(new LinkedHashSet<>(categories));
    }

    /** 대분류를 하나도 고르지 않았으면 랜덤 추천 (직접 선택 + 빈 목록도 랜덤, 명세 1.6) */
    public boolean isRandom() {
        return categories.isEmpty();
    }

    public boolean hasDestination() {
        return destination != null;
    }
}
