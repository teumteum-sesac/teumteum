package com.teumteum.recommend.service;

import com.teumteum.global.common.category.SubCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StayTimeCalculatorTest {

    private final StayTimeCalculator stayTimeCalculator = new StayTimeCalculator();

    @Test
    @DisplayName("카페는 체류시간표에 따라 40분이다")
    void calculate_returnsStayMinutes_forCafe() {
        int result = stayTimeCalculator.calculate("카페");

        assertEquals(40, result);
    }

    @Test
    @DisplayName("영화는 체류시간표에 따라 150분이다")
    void calculate_returnsStayMinutes_forMovie() {
        // when
        int result = stayTimeCalculator.calculate("영화");

        // then
        assertEquals(150, result);
    }

    @Test
    @DisplayName("영화·공연만 150분이고, 나머지 세분류는 모두 40분이다 (팀 결정 2026-11-18)")
    void allSubCategories_are40_exceptMovieAndPerformance() {
        for (SubCategory subCategory : SubCategory.values()) {
            int expected = (subCategory == SubCategory.MOVIE || subCategory == SubCategory.PERFORMANCE) ? 150 : 40;

            assertEquals(expected, stayTimeCalculator.calculate(subCategory.getDisplayName()),
                    subCategory.getDisplayName()); // 실패하면 어느 세분류인지 메시지로 보인다
        }
    }

    @Test
    @DisplayName("기본 체류시간은 40분이다")
    void defaultStayMinutes_is40() {
        assertEquals(40, StayTimeCalculator.DEFAULT_STAY_MINUTES);
    }

    @Test
    @DisplayName("체류시간표에 없는 세분류는 기본 체류시간을 쓴다")
    void calculate_returnsDefault_whenSubCategoryNotFound() {
        // when
        int result = stayTimeCalculator.calculate("사우나");

        // then
        assertEquals(StayTimeCalculator.DEFAULT_STAY_MINUTES, result);
    }

    @Test
    @DisplayName("세분류가 null이면 기본 체류시간을 쓴다")
    void calculate_returnsDefault_whenSubCategoryIsNull() {
        // when
        int result = stayTimeCalculator.calculate(null);

        // then
        assertEquals(StayTimeCalculator.DEFAULT_STAY_MINUTES, result);
    }
}
