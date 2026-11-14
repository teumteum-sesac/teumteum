package com.teumteum.recommend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StayTimeCalculatorTest {

    private final StayTimeCalculator stayTimeCalculator = new StayTimeCalculator();

    @Test
    @DisplayName("카페는 체류시간표에 따라 60분이다")
    void calculate_returnsStayMinutes_forCafe() {
        int result = stayTimeCalculator.calculate("카페");

        assertEquals(60, result);
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
