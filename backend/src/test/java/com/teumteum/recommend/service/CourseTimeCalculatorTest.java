package com.teumteum.recommend.service;

import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.PlaceVisit;
import com.teumteum.recommend.domain.TravelRoute;
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

    // ===================== toCoursePlan =====================

    @Test
    @DisplayName("방문지로 코스를 만들면 총 시간·이동 합(도착지 포함)을 채우고, 추정 구간이 없으면 estimated false")
    void toCoursePlan_fillsTotals() {
        List<CourseStop> stops = List.of(
                new CourseStop(place("1"), 60, new TravelRoute(15, List.of())),
                new CourseStop(place("2"), 90, new TravelRoute(10, List.of())));

        CoursePlan plan = courseTimeCalculator.toCoursePlan(stops, new TravelRoute(20, List.of()));

        assertEquals(195, plan.totalMinutes());       // 15 + 60 + 10 + 90 + 20
        assertEquals(45, plan.totalTravelMinutes());  // 15 + 10 + 20
        assertEquals(20, plan.finalTravelMinutes());
        assertFalse(plan.estimated());
    }

    @Test
    @DisplayName("직선 추정 구간이 하나라도 있으면 estimated true, 도착지가 없으면 마지막 이동은 0")
    void toCoursePlan_estimated_noDestination() {
        List<CourseStop> stops = List.of(
                new CourseStop(place("1"), 60, new TravelRoute(15, List.of())),
                new CourseStop(place("2"), 90, TravelRoute.estimated(12)));

        CoursePlan plan = courseTimeCalculator.toCoursePlan(stops, null);

        assertEquals(177, plan.totalMinutes());       // 15 + 60 + 12 + 90
        assertEquals(0, plan.finalTravelMinutes());
        assertTrue(plan.estimated());
    }

    private PlaceCandidate place(String id) {
        return new PlaceCandidate(id, "장소" + id, Category.CAFE, "카페", "서울", 37.545, 127.056);
    }
}