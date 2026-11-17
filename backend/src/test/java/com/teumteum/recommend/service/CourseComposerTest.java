package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 코스 조합 테스트.
 * <p>
 * - 계산기 4개는 순수 계산이라 진짜 객체를 쓴다
 * - 이동시간 서비스는 가짜(@Mock). 기본은 "모든 구간 10분" 으로 답하게 해 두고, 필요한 테스트에서만 바꾼다
 * - 후보를 섞는 Random 은 씨앗(42)을 고정해 매번 같은 결과가 나오게 한다
 * <p>
 * 후보 좌표는 출발지에서 북쪽으로 조금씩 떨어뜨린다. 위도 0.001도 ≈ 111m → 도보 직선 추정 약 3분.
 */
@ExtendWith(MockitoExtension.class)
class CourseComposerTest {

    private static final Coordinate ORIGIN = new Coordinate(37.5446, 127.0559);
    private static final int API_MINUTES = 10;

    @Mock
    private TravelTimeService travelTimeService;

    private CourseComposer composer;

    @BeforeEach
    void setUp() {
        composer = new CourseComposer(new PlaceCountCalculator(), new StayTimeCalculator(),
                new StraightLineTravelCalculator(), new CourseTimeCalculator(), travelTimeService, new Random(42));

        // lenient: 이 답을 안 쓰는 테스트가 있어도 Mockito 가 "쓸데없는 설정" 이라고 실패시키지 않게 한다
        lenient().when(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new TravelRoute(API_MINUTES, List.of()));
    }

    // ===================== 기본 =====================

    @Test
    @DisplayName("후보가 없으면 빈 목록이다 (오류 아님)")
    void compose_returnsEmpty_whenNoCandidates() {
        assertTrue(composer.compose(randomCondition(180), List.of()).isEmpty());
    }

    @Test
    @DisplayName("후보가 넉넉하면 서로 다른 코스 3개를 만든다")
    void compose_returnsThreeDifferentCourses() {
        List<CoursePlan> courses = composer.compose(randomCondition(180), manyCandidates());

        assertEquals(3, courses.size());
        Set<Set<String>> placeSets = courses.stream().map(CoursePlan::placeIds).collect(Collectors.toSet());
        assertEquals(3, placeSets.size()); // 장소 묶음이 셋 다 다르다
    }

    // ===================== 시간·장소 수 =====================

    @Test
    @DisplayName("모든 코스는 이용 시간 안이고 장소 수 상한(180분 → 2곳)을 넘지 않는다")
    void compose_respectsAvailableTimeAndMaxPlaceCount() {
        List<CoursePlan> courses = composer.compose(randomCondition(180), manyCandidates());

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            assertTrue(course.totalMinutes() <= 180, "이용 시간 초과: " + course.totalMinutes());
            assertTrue(course.stops().size() <= 2, "장소 수 상한 초과: " + course.stops().size());
        }
    }

    @Test
    @DisplayName("이용 시간이 60분이면 1곳짜리 코스만 나온다")
    void compose_onePlace_whenOneHour() {
        List<PlaceCandidate> candidates = List.of(
                place("1", CategoryCode.CAFE, "베이커리", 0.001),  // 30분
                place("2", CategoryCode.CAFE, "디저트", 0.002),    // 40분
                place("3", CategoryCode.FOOD, "분식", 0.003));     // 40분

        List<CoursePlan> courses = composer.compose(randomCondition(60), candidates);

        assertFalse(courses.isEmpty());
        courses.forEach(course -> assertEquals(1, course.stops().size()));
    }

    @Test
    @DisplayName("상한 안에서도 시간이 부족하면 더 적게 넣는다: 3시간에 영화(150분)가 들어가면 1곳 (REQ-019 예시)")
    void compose_fewerPlaces_whenLongStay() {
        CourseCondition condition = directCondition(180, CategoryCode.CULTURE, CategoryCode.CAFE);
        List<PlaceCandidate> candidates = List.of(
                place("1", CategoryCode.CULTURE, "영화", 0.001),   // 150분
                place("2", CategoryCode.CAFE, "베이커리", 0.002)); // 30분 → 150 + 30 + 이동 > 180

        List<CoursePlan> courses = composer.compose(condition, candidates);

        CoursePlan movieCourse = courses.stream()
                .filter(course -> subCategoriesOf(course).contains("영화"))
                .findFirst().orElseThrow();
        assertEquals(1, movieCourse.stops().size());
    }

    // ===================== 카테고리 규칙 =====================

    @Test
    @DisplayName("직접 선택(카페·음식)이면 코스에 두 대분류가 모두 들어간다 (번갈아 넣기)")
    void compose_direct_includesEachChosenCategory() {
        CourseCondition condition = directCondition(180, CategoryCode.CAFE, CategoryCode.FOOD);

        List<CoursePlan> courses = composer.compose(condition, manyCandidates());

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            Set<CategoryCode> categories = course.stops().stream()
                    .map(stop -> stop.place().category())
                    .collect(Collectors.toSet());
            assertEquals(Set.of(CategoryCode.CAFE, CategoryCode.FOOD), categories);
        }
    }

    @Test
    @DisplayName("한 코스 안에 같은 세분류는 두 번 들어가지 않는다")
    void compose_noDuplicateSubCategoryInCourse() {
        // 카페가 잔뜩 있어도 한 코스에 카페는 1곳만
        List<PlaceCandidate> candidates = List.of(
                place("1", CategoryCode.CAFE, "카페", 0.001),
                place("2", CategoryCode.CAFE, "카페", 0.002),
                place("3", CategoryCode.CAFE, "카페", 0.003),
                place("4", CategoryCode.FOOD, "분식", 0.004),
                place("5", CategoryCode.NATURE, "공원", 0.005));

        List<CoursePlan> courses = composer.compose(randomCondition(300), candidates);

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            List<String> subCategories = subCategoriesOf(course);
            assertEquals(subCategories.size(), new HashSet<>(subCategories).size(), "세분류 중복: " + subCategories);
        }
    }

    // ===================== 방문 순서 =====================

    @Test
    @DisplayName("방문 순서는 출발지에서 가까운 곳부터다")
    void orderByNearest_visitsClosestFirst() {
        PlaceCandidate far = place("far", CategoryCode.CAFE, "카페", 0.009);
        PlaceCandidate near = place("near", CategoryCode.FOOD, "한식", 0.001);
        PlaceCandidate middle = place("middle", CategoryCode.NATURE, "공원", 0.005);

        List<PlaceCandidate> ordered = composer.orderByNearest(ORIGIN, List.of(far, near, middle));

        assertEquals(List.of(near, middle, far), ordered);
    }

    // ===================== 확정하기 (이동시간 API) =====================

    @Test
    @DisplayName("실제 이동시간으로 넘으면 마지막 장소를 빼서 이용 시간 안으로 맞춘다")
    void confirmCourse_dropsLastPlace_whenApiTimeExceeds() {
        given(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(new TravelRoute(100, List.of())); // 구간마다 100분 (어림보다 훨씬 김)
        List<PlaceCandidate> places = List.of(
                place("1", CategoryCode.CAFE, "베이커리", 0.001),  // 30분
                place("2", CategoryCode.FOOD, "분식", 0.002));     // 40분

        // 2곳: 100 + 30 + 100 + 40 = 270 > 180 → 1곳: 100 + 30 = 130
        CoursePlan course = composer.confirmCourse(randomCondition(180), places).orElseThrow();

        assertEquals(1, course.stops().size());
        assertEquals("1", course.stops().get(0).place().placeId()); // 앞 장소는 남는다
        assertEquals(130, course.totalMinutes());
    }

    @Test
    @DisplayName("한 곳만 남겨도 넘으면 그 코스는 버린다")
    void confirmCourse_discards_whenEvenOnePlaceExceeds() {
        given(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(new TravelRoute(200, List.of()));

        assertTrue(composer.confirmCourse(randomCondition(180),
                List.of(place("1", CategoryCode.CAFE, "베이커리", 0.001))).isEmpty());
    }

    @Test
    @DisplayName("도착지가 있으면 마지막 장소 → 도착지 이동도 총 소요시간에 들어간다")
    void confirmCourse_includesFinalTravel_whenDestination() {
        CourseCondition condition = new CourseCondition(ORIGIN, new Coordinate(37.5546, 127.0559),
                180, TransportType.WALK, List.of());
        List<PlaceCandidate> places = List.of(place("1", CategoryCode.CAFE, "베이커리", 0.001)); // 30분

        CoursePlan course = composer.confirmCourse(condition, places).orElseThrow();

        assertEquals(API_MINUTES, course.finalTravelMinutes());
        assertEquals(API_MINUTES * 2, course.totalTravelMinutes());      // 출발지 → 장소, 장소 → 도착지
        assertEquals(30 + API_MINUTES * 2, course.totalMinutes());
    }

    @Test
    @DisplayName("도착지가 없으면 돌아오는 시간은 계산하지 않는다 (finalTravel 없음, 0분)")
    void confirmCourse_noFinalTravel_withoutDestination() {
        CoursePlan course = composer.confirmCourse(randomCondition(180),
                List.of(place("1", CategoryCode.CAFE, "베이커리", 0.001))).orElseThrow();

        assertEquals(0, course.finalTravelMinutes());
        assertEquals(API_MINUTES, course.totalTravelMinutes());
    }

    @Test
    @DisplayName("직선거리로 추정한 구간이 하나라도 있으면 코스의 estimated 가 true 다")
    void confirmCourse_estimated_whenAnyLegEstimated() {
        given(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(new TravelRoute(API_MINUTES, List.of()), TravelRoute.estimated(5)); // 첫 구간 API, 둘째 추정
        List<PlaceCandidate> places = List.of(
                place("1", CategoryCode.CAFE, "베이커리", 0.001),
                place("2", CategoryCode.FOOD, "분식", 0.002));

        CoursePlan course = composer.confirmCourse(randomCondition(300), places).orElseThrow();

        assertTrue(course.estimated());
    }

    @Test
    @DisplayName("이동시간 API 는 최종 코스의 구간에만 부른다 (고르는 동안은 부르지 않음, NFR-014)")
    void compose_callsApiOnlyForFinalCourses() {
        List<CoursePlan> courses = composer.compose(randomCondition(180), manyCandidates());

        // 도착지 없음 + 줄이기 없음 → 구간 수 = 코스들의 장소 수 합
        int legCount = courses.stream().mapToInt(course -> course.stops().size()).sum();
        verify(travelTimeService, times(legCount))
                .findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertNotEquals(0, legCount);
    }

    @Test
    @DisplayName("이미 찾은 코스가 있으면 모자란 만큼만 새로 만들고, 같은 코스는 다시 만들지 않는다")
    void compose_fillsOnlyMissingCourses_whenAlreadyFound() {
        List<CoursePlan> firstRound = composer.compose(randomCondition(180), manyCandidates());
        List<CoursePlan> alreadyFound = firstRound.subList(0, 1); // 1개는 이미 찾았다고 치기

        List<CoursePlan> added = composer.compose(randomCondition(180), manyCandidates(), alreadyFound);

        assertEquals(2, added.size()); // 3 - 1 = 2개만
        added.forEach(course -> assertNotEquals(alreadyFound.get(0).placeIds(), course.placeIds()));
    }

    @Test
    @DisplayName("이미 3개를 찾았으면 아무것도 만들지 않고 API 도 부르지 않는다")
    void compose_doesNothing_whenAlreadyThree() {
        List<CoursePlan> three = composer.compose(randomCondition(180), manyCandidates());
        clearInvocations(travelTimeService); // 위에서 부른 기록은 지우고 아래만 센다

        List<CoursePlan> added = composer.compose(randomCondition(180), manyCandidates(), three);

        assertTrue(added.isEmpty());
        verifyNoInteractions(travelTimeService);
    }

    // ===================== 조건 정리 =====================

    @Test
    @DisplayName("조건의 대분류 중복은 하나로 정리되고, 비어 있으면 랜덤이다")
    void courseCondition_normalizesCategories() {
        CourseCondition duplicated = directCondition(180, CategoryCode.CAFE, CategoryCode.CAFE, CategoryCode.FOOD);
        CourseCondition empty = new CourseCondition(ORIGIN, null, 180, TransportType.WALK, null);

        assertEquals(List.of(CategoryCode.CAFE, CategoryCode.FOOD), duplicated.categories());
        assertTrue(empty.isRandom());
    }

    // ===================== 도우미 =====================

    private CourseCondition randomCondition(int availableMinutes) {
        return new CourseCondition(ORIGIN, null, availableMinutes, TransportType.WALK, List.of());
    }

    private CourseCondition directCondition(int availableMinutes, CategoryCode... categories) {
        return new CourseCondition(ORIGIN, null, availableMinutes, TransportType.WALK, List.of(categories));
    }

    /** 출발지에서 북쪽으로 latOffset 만큼 떨어진 후보 */
    private PlaceCandidate place(String id, CategoryCode category, String subCategory, double latOffset) {
        return new PlaceCandidate(id, "장소" + id, category, subCategory, "서울 성동구",
                ORIGIN.latitude() + latOffset, ORIGIN.longitude());
    }

    /** 대분류·세분류가 고루 섞인 후보 8곳 */
    private List<PlaceCandidate> manyCandidates() {
        return List.of(
                place("1", CategoryCode.CAFE, "카페", 0.001),       // 60분
                place("2", CategoryCode.CAFE, "베이커리", 0.002),   // 30분
                place("3", CategoryCode.CAFE, "디저트", 0.003),     // 40분
                place("4", CategoryCode.FOOD, "한식", 0.001),       // 60분
                place("5", CategoryCode.FOOD, "분식", 0.004),       // 40분
                place("6", CategoryCode.FOOD, "일식", 0.002),       // 60분
                place("7", CategoryCode.NATURE, "공원", 0.003),     // 60분
                place("8", CategoryCode.SHOPPING, "소품샵", 0.005)); // 40분
    }

    private List<String> subCategoriesOf(CoursePlan course) {
        return course.stops().stream().map(CourseStop::place).map(PlaceCandidate::subCategory).toList();
    }
}
