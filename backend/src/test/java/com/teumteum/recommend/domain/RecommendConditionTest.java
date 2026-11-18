package com.teumteum.recommend.domain;

import com.teumteum.recommend.domain.RecommendCondition.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 추천 조건 정리 테스트 (명세 1.6 서버 처리 순서 1).
 */
class RecommendConditionTest {

    private static final Location ORIGIN = new Location("성수역", "서울 성동구 아차산로 100", 37.5446, 127.0559);

    @Test
    @DisplayName("직접 선택인데 대분류가 비어 있으면 랜덤으로 바뀐다")
    void direct_withoutCategories_becomesRandom() {
        RecommendCondition condition = conditionOf(CategoryMode.DIRECT, List.of());

        assertEquals(CategoryMode.RANDOM, condition.categoryMode());
    }

    @Test
    @DisplayName("직접 선택인데 대분류가 null 이어도 랜덤으로 바뀐다")
    void direct_withNullCategories_becomesRandom() {
        RecommendCondition condition = conditionOf(CategoryMode.DIRECT, null);

        assertEquals(CategoryMode.RANDOM, condition.categoryMode());
        assertTrue(condition.categories().isEmpty());
    }

    @Test
    @DisplayName("랜덤이면 대분류를 보내도 무시한다")
    void random_ignoresCategories() {
        RecommendCondition condition = conditionOf(CategoryMode.RANDOM, List.of(CategoryCode.CAFE));

        assertTrue(condition.categories().isEmpty());
    }

    @Test
    @DisplayName("대분류 중복은 하나로 정리하고 처음 순서를 지킨다")
    void direct_removesDuplicateCategories() {
        RecommendCondition condition = conditionOf(CategoryMode.DIRECT,
                List.of(CategoryCode.SPORTS, CategoryCode.CAFE, CategoryCode.SPORTS));

        assertEquals(CategoryMode.DIRECT, condition.categoryMode());
        assertEquals(List.of(CategoryCode.SPORTS, CategoryCode.CAFE), condition.categories());
    }

    @Test
    @DisplayName("코스 계산용 조건으로 바꾸면 좌표·이용 시간·이동수단·대분류만 남는다")
    void toCourseCondition_keepsCalculationFields() {
        RecommendCondition condition = conditionOf(CategoryMode.DIRECT, List.of(CategoryCode.CAFE));

        CourseCondition courseCondition = condition.toCourseCondition();

        assertEquals(37.5446, courseCondition.origin().latitude());
        assertNull(courseCondition.destination()); // 도착지 없음
        assertEquals(180, courseCondition.availableMinutes());
        assertEquals(List.of(CategoryCode.CAFE), courseCondition.categories());
    }

    private RecommendCondition conditionOf(CategoryMode mode, List<CategoryCode> categories) {
        return new RecommendCondition(ORIGIN, null, "13:00", 180, TransportType.TRANSIT, mode, categories);
    }
}
