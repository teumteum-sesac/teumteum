package com.teumteum.recommend.domain;

import com.teumteum.global.common.category.Category;

import com.teumteum.recommend.domain.TravelRoute.Coordinate;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 서버가 실제로 적용한 추천 조건 전체 (보관용).
 * <p>
 * CourseCondition 과의 차이
 * - CourseCondition: 코스 "계산" 에 필요한 것만 (좌표·이용 시간·이동수단·대분류)
 * - RecommendCondition: 화면에 다시 보여줄 것까지 (출발지·도착지 이름과 주소, 시작 시각, 카테고리 선택 방식)
 * 응답의 condition(조건 요약 칩·'조건 바꾸기')과 다시 추천(요청 본문 없이 보관한 조건 사용)에 쓴다.
 *
 * @param origin           출발지 (필수)
 * @param destination      도착지. 없으면 null
 * @param startTime        시작 시각 "HH:mm"
 * @param availableMinutes 이용 시간(분)
 * @param transportType    이동수단
 * @param categoryMode     실제 적용한 선택 방식. DIRECT 인데 대분류가 비었으면 RANDOM 으로 바뀌어 있다
 * @param categories       실제 적용한 대분류 (중복 제거). RANDOM 이면 빈 목록
 */
public record RecommendCondition(
        Location origin,
        Location destination,
        String startTime,
        int availableMinutes,
        TransportType transportType,
        CategoryMode categoryMode,
        List<Category> categories
) {

    /**
     * 요청 값을 "서버가 실제로 적용할 값" 으로 정리한다 (명세 1.6 서버 처리 순서 1).
     * - RANDOM 이면 대분류는 무시 → 빈 목록
     * - DIRECT 인데 대분류가 비었으면 → RANDOM
     * - 대분류 중복은 하나로 (순서 유지)
     */
    public RecommendCondition {
        List<Category> distinct = (categories == null) ? List.of() : List.copyOf(new LinkedHashSet<>(categories));
        if (categoryMode == CategoryMode.RANDOM || distinct.isEmpty()) {
            categoryMode = CategoryMode.RANDOM;
            distinct = List.of();
        }
        categories = distinct;
    }

    /** 코스 계산용 조건으로 바꾼다 (이름·주소·시작 시각은 계산에 필요 없음) */
    public CourseCondition toCourseCondition() {
        return new CourseCondition(
                origin.toCoordinate(),
                destination == null ? null : destination.toCoordinate(),
                availableMinutes,
                transportType,
                categories);
    }

    /**
     * 출발지·도착지 1곳.
     *
     * @param name    장소 이름 (예: "성수역"). 없으면 null
     * @param address 주소. 없으면 null
     */
    public record Location(String name, String address, double latitude, double longitude) {

        public Coordinate toCoordinate() {
            return new Coordinate(latitude, longitude);
        }
    }
}
