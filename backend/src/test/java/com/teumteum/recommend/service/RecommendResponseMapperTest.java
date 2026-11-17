package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.RecommendCondition;
import com.teumteum.recommend.domain.RecommendCondition.Location;
import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.dto.RecommendCourseResponse;
import com.teumteum.recommend.dto.RecommendPlaceResponse;
import com.teumteum.recommend.dto.RecommendResponse;
import com.teumteum.recommend.dto.SubCategoryFilterResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 보관한 추천 결과 → 응답 DTO 변환 테스트 (명세 1.3 모양).
 */
class RecommendResponseMapperTest {

    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.of(2026, 11, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final Location ORIGIN = new Location("성수역", "서울 성동구 아차산로 100", 37.5446, 127.0559);

    private final RecommendResponseMapper mapper = new RecommendResponseMapper();

    // ===================== 코스 · 장소 =====================

    @Test
    @DisplayName("코스명은 장소 이름을 방문 순서대로 → 로 잇고, 방문 순서는 1부터다")
    void courseNameAndVisitOrder() {
        RecommendCourseResponse course = mapper.toCourseResponse(twoPlaceCourse(), "13:00");

        assertEquals("당구장 A → 소품샵 B", course.courseName());
        assertEquals(2, course.placeCount());
        assertEquals(1, course.places().get(0).visitOrder());
        assertEquals(2, course.places().get(1).visitOrder());
        assertEquals("13:00", course.startTime());
    }

    @Test
    @DisplayName("시간·추정 여부는 코스 계산 결과를 그대로 쓴다")
    void courseTimes() {
        RecommendCourseResponse course = mapper.toCourseResponse(twoPlaceCourse(), "13:00");

        assertEquals(160, course.totalMinutes());
        assertEquals(30, course.totalTravelMinutes());
        assertEquals(7, course.finalTravelMinutes());
        assertTrue(course.estimated());
    }

    @Test
    @DisplayName("장소별 이동시간·노선은 직전 지점에서 오는 구간 값이고, 사진·운영시간은 아직 null 이다")
    void placeFields() {
        RecommendPlaceResponse first = mapper.toCourseResponse(twoPlaceCourse(), "13:00").places().get(0);

        assertEquals("101", first.placeId());
        assertEquals(CategoryCode.SPORTS, first.category());
        assertEquals("당구", first.subCategory());
        assertEquals(90, first.stayMinutes());
        assertEquals(15, first.travelMinutes());
        assertEquals("버스 721 · 환승 0회", first.transitInfo());
        assertNull(first.imageUrl());
        assertNull(first.openingHours());
    }

    // ===================== 조건 =====================

    @Test
    @DisplayName("응답 조건에는 실제 적용한 값이 나간다 (직접 선택 + 빈 대분류 → RANDOM)")
    void conditionShowsAppliedValues() {
        RecommendCondition condition = conditionOf(CategoryMode.DIRECT, List.of());

        RecommendResponse response = mapper.toResponse(setOf(condition, List.of()));

        assertEquals(CategoryMode.RANDOM, response.condition().categoryMode());
        assertEquals("성수역", response.condition().origin().name());
        assertNull(response.condition().destination());
    }

    @Test
    @DisplayName("코스가 없으면 courses 는 빈 목록이다 (200 으로 응답, REQ-027)")
    void emptyCourses() {
        RecommendResponse response = mapper.toResponse(setOf(conditionOf(CategoryMode.RANDOM, List.of()), List.of()));

        assertTrue(response.courses().isEmpty());
        assertTrue(response.subCategoryFilters().isEmpty());
        assertEquals("rs_1", response.recommendationSetId());
        assertEquals(EXPIRES_AT, response.expiresAt());
    }

    // ===================== 세분류 필터 =====================

    @Test
    @DisplayName("직접 선택이면 고른 대분류 순서대로, 그 대분류의 세분류 전체를 묶는다")
    void filters_direct_allSubCategoriesOfChosenCategories() {
        RecommendCondition condition = conditionOf(CategoryMode.DIRECT, List.of(CategoryCode.SPORTS, CategoryCode.SHOPPING));

        List<SubCategoryFilterResponse> filters = mapper.toSubCategoryFilters(condition, List.of());

        assertEquals(2, filters.size());
        assertEquals(CategoryCode.SPORTS, filters.get(0).category());
        assertEquals("스포츠활동", filters.get(0).categoryName());
        assertEquals(List.of("당구", "볼링", "클라이밍"), filters.get(0).subCategories());
        assertEquals(List.of("소품샵", "팝업스토어", "시장", "백화점"), filters.get(1).subCategories());
    }

    @Test
    @DisplayName("랜덤이면 결과에 나온 세분류만, 대분류 표 순서대로 묶는다")
    void filters_random_onlyAppearedSubCategories() {
        RecommendCondition condition = conditionOf(CategoryMode.RANDOM, List.of());

        // 결과: 당구(SPORTS), 소품샵(SHOPPING)
        List<SubCategoryFilterResponse> filters = mapper.toSubCategoryFilters(condition, List.of(twoPlaceCourse()));

        assertEquals(2, filters.size());
        assertEquals(CategoryCode.SHOPPING, filters.get(0).category()); // 대분류 표에서 SHOPPING 이 SPORTS 보다 앞
        assertEquals(List.of("소품샵"), filters.get(0).subCategories());
        assertEquals(CategoryCode.SPORTS, filters.get(1).category());
        assertEquals(List.of("당구"), filters.get(1).subCategories());
    }

    // ===================== 도우미 =====================

    /** 명세 1.3 예시와 같은 코스: 당구장 A(버스 15분) → 소품샵 B(8분) → 도착지(7분) */
    private RecommendedCourse twoPlaceCourse() {
        CourseStop billiards = new CourseStop(
                new PlaceCandidate("101", "당구장 A", CategoryCode.SPORTS, "당구", "서울 성동구 ○○로 1", 37.5470, 127.0500),
                90, new TravelRoute(15, List.of(), "버스 721 · 환승 0회"));
        CourseStop propShop = new CourseStop(
                new PlaceCandidate("102", "소품샵 B", CategoryCode.SHOPPING, "소품샵", "서울 성동구 ○○로 2", 37.5460, 127.0420),
                40, TravelRoute.estimated(8));
        CoursePlan plan = new CoursePlan(List.of(billiards, propShop), new TravelRoute(7, List.of()), 160, 30, true);
        return new RecommendedCourse("rc_1", "rs_1", plan, EXPIRES_AT);
    }

    private RecommendCondition conditionOf(CategoryMode mode, List<CategoryCode> categories) {
        return new RecommendCondition(ORIGIN, null, "13:00", 180, TransportType.TRANSIT, mode, categories);
    }

    private RecommendationSet setOf(RecommendCondition condition, List<RecommendedCourse> courses) {
        return new RecommendationSet("rs_1", condition, courses, EXPIRES_AT.minusHours(2), EXPIRES_AT);
    }
}
