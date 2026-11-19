package com.teumteum.recommend.service;

import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TravelRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 남는 시간을 체류시간으로 채우기 테스트 (#204). 순수 계산이라 진짜 객체만 쓴다.
 */
class StayTimeFillerTest {

    private final CourseTimeCalculator courseTimeCalculator = new CourseTimeCalculator();
    private final StayTimeFiller filler = new StayTimeFiller(new StayTimeCalculator(), courseTimeCalculator);

    @Test
    @DisplayName("남는 30분을 두 장소에 5분씩 번갈아 나눈다 (40·40 → 55·55)")
    void fill_splitsRemainingEvenly() {
        CoursePlan course = plan(null, stop("1", "카페", 40, 5), stop("2", "공원", 40, 5)); // 90분

        CoursePlan filled = filler.fill(course, 120);

        assertEquals(55, filled.stops().get(0).stayMinutes());
        assertEquals(55, filled.stops().get(1).stayMinutes());
        assertEquals(120, filled.totalMinutes());
        assertEquals(10, filled.totalTravelMinutes()); // 이동시간은 그대로
    }

    @Test
    @DisplayName("5분 단위로만 늘린다: 남는 시간이 12분이면 10분만 쓴다")
    void fill_usesFiveMinuteSteps() {
        CoursePlan course = plan(null, stop("1", "카페", 40, 8)); // 48분

        CoursePlan filled = filler.fill(course, 60);

        assertEquals(50, filled.stops().get(0).stayMinutes());
        assertEquals(58, filled.totalMinutes());
    }

    @Test
    @DisplayName("최대 체류시간(카페 90분)을 넘기지 않는다. 다 차면 남은 시간은 그대로 둔다")
    void fill_stopsAtMaxStay() {
        CoursePlan course = plan(null, stop("1", "카페", 40, 10)); // 50분

        CoursePlan filled = filler.fill(course, 300);

        assertEquals(90, filled.stops().get(0).stayMinutes());
        assertEquals(100, filled.totalMinutes());
    }

    @Test
    @DisplayName("영화는 150 ~ 180분 안에서 늘린다")
    void fill_movieUpTo180() {
        CoursePlan course = plan(null, stop("1", "영화", 150, 10)); // 160분

        CoursePlan filled = filler.fill(course, 240);

        assertEquals(180, filled.stops().get(0).stayMinutes());
    }

    @Test
    @DisplayName("한 장소가 최대에 닿으면 나머지 장소로 계속 채운다")
    void fill_continuesWithOtherPlaces_whenOneIsFull() {
        CoursePlan course = plan(null, stop("1", "카페", 85, 5), stop("2", "공원", 40, 5)); // 135분

        CoursePlan filled = filler.fill(course, 165);

        assertEquals(90, filled.stops().get(0).stayMinutes()); // 5분만 더 들어감
        assertEquals(65, filled.stops().get(1).stayMinutes()); // 나머지 25분
        assertEquals(165, filled.totalMinutes());
    }

    @Test
    @DisplayName("도착지 이동이 있으면 그 시간도 총 시간에 그대로 들어간다")
    void fill_keepsFinalTravel() {
        TravelRoute finalTravel = new TravelRoute(20, List.of());
        CoursePlan course = plan(finalTravel, stop("1", "카페", 40, 10)); // 70분

        CoursePlan filled = filler.fill(course, 90);

        assertEquals(60, filled.stops().get(0).stayMinutes());
        assertEquals(90, filled.totalMinutes());
        assertEquals(20, filled.finalTravelMinutes());
    }

    @Test
    @DisplayName("남는 시간이 5분 미만이거나 이미 목표를 넘었으면 원래 코스 그대로")
    void fill_returnsSameCourse_whenNoRoom() {
        CoursePlan course = plan(null, stop("1", "카페", 40, 10)); // 50분

        assertSame(course, filler.fill(course, 54));
        assertSame(course, filler.fill(course, 40));
    }

    // ===================== 도우미 =====================

    private CourseStop stop(String id, String subCategory, int stayMinutes, int travelMinutes) {
        PlaceCandidate place = new PlaceCandidate(id, "장소" + id, Category.CAFE, subCategory, "서울 성동구",
                37.5446, 127.0559);
        return new CourseStop(place, stayMinutes, new TravelRoute(travelMinutes, List.of()));
    }

    private CoursePlan plan(TravelRoute finalTravel, CourseStop... stops) {
        return courseTimeCalculator.toCoursePlan(List.of(stops), finalTravel);
    }
}
