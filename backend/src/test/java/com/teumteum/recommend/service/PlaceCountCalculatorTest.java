package com.teumteum.recommend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceCountCalculatorTest {

    private final PlaceCountCalculator placeCountCalculator = new PlaceCountCalculator();

    @Test
    @DisplayName("이용 시간이 30분(최솟값)이면 최대 1곳이다")
    void calculateMaxPlaceCount_returnsOne_when30Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(30);

        // then
        assertEquals(1, result);
    }

    @Test
    @DisplayName("이용 시간이 60분이면 최대 1곳이다")
    void calculateMaxPlaceCount_returnsOne_when60Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(60);

        // then
        assertEquals(1, result);
    }

    @Test
    @DisplayName("이용 시간이 61분이면 최대 2곳이다")
    void calculateMaxPlaceCount_returnsTwo_when61Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(61);

        // then
        assertEquals(2, result);
    }

    @Test
    @DisplayName("이용 시간이 180분이면 최대 2곳이다")
    void calculateMaxPlaceCount_returnsTwo_when180Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(180);

        // then
        assertEquals(2, result);
    }

    @Test
    @DisplayName("이용 시간이 181분이면 최대 3곳이다")
    void calculateMaxPlaceCount_returnsThree_when181Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(181);

        // then
        assertEquals(3, result);
    }

    @Test
    @DisplayName("이용 시간이 300분이면 최대 3곳이다")
    void calculateMaxPlaceCount_returnsThree_when300Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(300);

        // then
        assertEquals(3, result);
    }

    @Test
    @DisplayName("이용 시간이 301분이면 최대 장소 수(4곳)이다")
    void calculateMaxPlaceCount_returnsMax_when301Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(301);

        // then
        assertEquals(PlaceCountCalculator.MAX_PLACE_COUNT, result);
    }

    @Test
    @DisplayName("이용 시간이 360분(최댓값)이면 최대 장소 수(4곳)이다")
    void calculateMaxPlaceCount_returnsMax_when360Minutes() {
        // when
        int result = placeCountCalculator.calculateMaxPlaceCount(360);

        // then
        assertEquals(PlaceCountCalculator.MAX_PLACE_COUNT, result);
    }
}