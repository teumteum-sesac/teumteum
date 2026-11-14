package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.PlaceVisit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseTimeCalculatorTest {

    private final CourseTimeCalculator courseTimeCalculator = new CourseTimeCalculator();

    @Test
    @DisplayName("장소 2곳에 도착지가 있으면 이동·체류·마지막 이동을 모두 더한다")
    void calculateTotalMinutes_returnsSum_withDestination() {
        // given
        List<PlaceVisit> visits = List.of(
                new PlaceVisit(15, 60),
                new PlaceVisit(10, 90)
        );

        // when
        int result = courseTimeCalculator.calculateTotalMinutes(visits, 20);

        // then
        assertEquals(195, result);
    }

    @Test
    @DisplayName("도착지가 없으면 마지막 이동 없이 이동·체류만 더한다")
    void calculateTotalMinutes_returnsSum_withoutDestination() {
        // given
        List<PlaceVisit> visits = List.of(
                new PlaceVisit(15, 60),
                new PlaceVisit(10, 90)
        );

        // when
        int result = courseTimeCalculator.calculateTotalMinutes(visits, 0);

        // then
        assertEquals(175, result);
    }

    @Test
    @DisplayName("장소가 1곳이면 출발지 이동과 체류만 더한다")
    void calculateTotalMinutes_returnsSum_forSinglePlace() {
        // given
        List<PlaceVisit> visits = List.of(new PlaceVisit(15, 60));

        // when
        int result = courseTimeCalculator.calculateTotalMinutes(visits, 0);

        // then
        assertEquals(75, result);
    }

    @Test
    @DisplayName("총 소요시간이 이용 시간과 같으면 통과한다")
    void isWithinAvailableTime_returnsTrue_whenEqual() {
        // when
        boolean result = courseTimeCalculator.isWithinAvailableTime(180, 180);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("총 소요시간이 이용 시간보다 짧으면 통과한다")
    void isWithinAvailableTime_returnsTrue_whenLess() {
        // when
        boolean result = courseTimeCalculator.isWithinAvailableTime(175, 180);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("총 소요시간이 이용 시간을 1분이라도 넘으면 실패한다")
    void isWithinAvailableTime_returnsFalse_whenOneMinuteOver() {
        // when
        boolean result = courseTimeCalculator.isWithinAvailableTime(181, 180);

        // then
        assertFalse(result);
    }
}