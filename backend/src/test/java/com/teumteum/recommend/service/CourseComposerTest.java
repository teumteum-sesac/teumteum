package com.teumteum.recommend.service;

import com.teumteum.global.common.category.Category;
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
                new StraightLineTravelCalculator(), new CourseTimeCalculator(), travelTimeService,
                new StayTimeFiller(new StayTimeCalculator(), new CourseTimeCalculator()), new Random(42));

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
                place("1", Category.CAFE, "베이커리", 0.001),  // 40분
                place("2", Category.CAFE, "디저트", 0.002),    // 40분
                place("3", Category.FOOD, "분식", 0.003));     // 40분

        List<CoursePlan> courses = composer.compose(randomCondition(60), candidates);

        assertFalse(courses.isEmpty());
        courses.forEach(course -> assertEquals(1, course.stops().size()));
    }

    @Test
    @DisplayName("상한 안에서도 시간이 부족하면 더 적게 넣는다: 3시간에 영화(150분)가 들어가면 1곳 (REQ-019 예시)")
    void compose_fewerPlaces_whenLongStay() {
        CourseCondition condition = directCondition(180, Category.CULTURE, Category.CAFE);
        List<PlaceCandidate> candidates = List.of(
                place("1", Category.CULTURE, "영화", 0.001),   // 150분
                place("2", Category.CAFE, "베이커리", 0.002)); // 40분 → 150 + 40 + 이동 > 180

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
        CourseCondition condition = directCondition(180, Category.CAFE, Category.FOOD);

        List<CoursePlan> courses = composer.compose(condition, manyCandidates());

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            Set<Category> categories = course.stops().stream()
                    .map(stop -> stop.place().category())
                    .collect(Collectors.toSet());
            assertEquals(Set.of(Category.CAFE, Category.FOOD), categories);
        }
    }

    @Test
    @DisplayName("한 코스 안에 같은 세분류는 두 번 들어가지 않는다")
    void compose_noDuplicateSubCategoryInCourse() {
        // 카페가 잔뜩 있어도 한 코스에 카페는 1곳만
        List<PlaceCandidate> candidates = List.of(
                place("1", Category.CAFE, "카페", 0.001),
                place("2", Category.CAFE, "카페", 0.002),
                place("3", Category.CAFE, "카페", 0.003),
                place("4", Category.FOOD, "분식", 0.004),
                place("5", Category.NATURE, "공원", 0.005));

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
        PlaceCandidate far = place("far", Category.CAFE, "카페", 0.009);
        PlaceCandidate near = place("near", Category.FOOD, "한식", 0.001);
        PlaceCandidate middle = place("middle", Category.NATURE, "공원", 0.005);

        List<PlaceCandidate> ordered = composer.orderByNearest(ORIGIN, List.of(far, near, middle));

        assertEquals(List.of(near, middle, far), ordered);
    }

    // ===================== 남는 시간 채우기 (#204) =====================

    @Test
    @DisplayName("코스마다 채우는 목표가 80% / 90% / 100% 다 (120분 → 96 / 108 / 120분)")
    void fillTargetMinutes_byCourseIndex() {
        CourseCondition condition = randomCondition(120);

        assertEquals(96, composer.fillTargetMinutes(condition, 0));
        assertEquals(108, composer.fillTargetMinutes(condition, 1));
        assertEquals(120, composer.fillTargetMinutes(condition, 2));
    }

    @Test
    @DisplayName("3시간이면 남는 시간을 체류시간으로 채워 세 코스가 144 / 162 / 180분 목표에 맞춰 서로 다르게 나온다")
    void compose_fillsStayTime_differentlyPerCourse() {
        List<CoursePlan> courses = composer.compose(randomCondition(180), manyCandidates());

        assertEquals(3, courses.size());
        for (int i = 0; i < courses.size(); i++) {
            int target = composer.fillTargetMinutes(randomCondition(180), i);
            int total = courses.get(i).totalMinutes();
            // 5분 단위로 채우므로 목표보다 5분 이상 모자라지 않는다 (최대 체류시간에 걸리지 않는 한)
            assertTrue(total <= target && total > target - StayTimeFiller.STEP_MINUTES,
                    i + "번째 코스 목표 " + target + "분, 실제 " + total + "분");
        }
        Set<Integer> totals = courses.stream().map(CoursePlan::totalMinutes).collect(Collectors.toSet());
        assertEquals(3, totals.size()); // 1:20대로 다 비슷하게 나오던 문제
    }

    // ===================== 확정하기 (이동시간 API) =====================

    @Test
    @DisplayName("실제 이동시간으로 넘으면 마지막 장소를 빼서 이용 시간 안으로 맞춘다")
    void confirmCourse_dropsLastPlace_whenApiTimeExceeds() {
        given(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(new TravelRoute(100, List.of())); // 구간마다 100분 (어림보다 훨씬 김)
        List<PlaceCandidate> places = List.of(
                place("1", Category.CAFE, "베이커리", 0.001),  // 40분
                place("2", Category.FOOD, "분식", 0.002));     // 40분

        // 2곳: 100 + 40 + 100 + 40 = 280 > 180 → 1곳: 100 + 40 = 140
        CoursePlan course = composer.confirmCourse(randomCondition(180), places).orElseThrow();

        assertEquals(1, course.stops().size());
        assertEquals("1", course.stops().get(0).place().placeId()); // 앞 장소는 남는다
        assertEquals(140, course.totalMinutes());
    }

    @Test
    @DisplayName("한 곳만 남겨도 넘으면 그 코스는 버린다")
    void confirmCourse_discards_whenEvenOnePlaceExceeds() {
        given(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(new TravelRoute(200, List.of()));

        assertTrue(composer.confirmCourse(randomCondition(180),
                List.of(place("1", Category.CAFE, "베이커리", 0.001))).isEmpty());
    }

    @Test
    @DisplayName("도착지가 있으면 마지막 장소 → 도착지 이동도 총 소요시간에 들어간다")
    void confirmCourse_includesFinalTravel_whenDestination() {
        CourseCondition condition = new CourseCondition(ORIGIN, new Coordinate(37.5546, 127.0559),
                180, TransportType.WALK, List.of());
        List<PlaceCandidate> places = List.of(place("1", Category.CAFE, "베이커리", 0.001)); // 40분

        CoursePlan course = composer.confirmCourse(condition, places).orElseThrow();

        assertEquals(API_MINUTES, course.finalTravelMinutes());
        assertEquals(API_MINUTES * 2, course.totalTravelMinutes());      // 출발지 → 장소, 장소 → 도착지
        assertEquals(40 + API_MINUTES * 2, course.totalMinutes());
    }

    @Test
    @DisplayName("도착지가 없으면 돌아오는 시간은 계산하지 않는다 (finalTravel 없음, 0분)")
    void confirmCourse_noFinalTravel_withoutDestination() {
        CoursePlan course = composer.confirmCourse(randomCondition(180),
                List.of(place("1", Category.CAFE, "베이커리", 0.001))).orElseThrow();

        assertEquals(0, course.finalTravelMinutes());
        assertEquals(API_MINUTES, course.totalTravelMinutes());
    }

    @Test
    @DisplayName("직선거리로 추정한 구간이 하나라도 있으면 코스의 estimated 가 true 다")
    void confirmCourse_estimated_whenAnyLegEstimated() {
        given(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(new TravelRoute(API_MINUTES, List.of()), TravelRoute.estimated(5)); // 첫 구간 API, 둘째 추정
        List<PlaceCandidate> places = List.of(
                place("1", Category.CAFE, "베이커리", 0.001),
                place("2", Category.FOOD, "분식", 0.002));

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

    // ===================== 너무 먼 곳 막기 (#170) =====================

    @Test
    @DisplayName("도보 한 구간이 25분을 넘는 장소는 시간이 남아도 코스에 넣지 않는다")
    void compose_walk_skipsPlaceOverLegLimit() {
        PlaceCandidate near = place("near", Category.CAFE, "카페", 0.001);   // 약 111m → 도보 약 3분
        PlaceCandidate far = place("far", Category.NATURE, "공원", 0.013);   // 약 1.4km → 도보 약 29분 (> 25분)

        List<CoursePlan> courses = composer.compose(randomCondition(300), List.of(near, far)); // 5시간: 시간은 충분

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            assertFalse(course.placeIds().contains("far"), "먼 장소가 들어감: " + course.placeIds());
        }
    }

    @Test
    @DisplayName("한 구간 판단: 출발지 → 장소, 장소 → 장소 구간만 보고 마지막 장소 → 도착지 구간은 보지 않는다")
    void isWithinLegLimit_ignoresFinalTravelToDestination() {
        PlaceCandidate near = place("near", Category.CAFE, "카페", 0.001);
        PlaceCandidate far = place("far", Category.NATURE, "공원", 0.013);
        Coordinate farDestination = new Coordinate(ORIGIN.latitude() + 0.05, ORIGIN.longitude()); // 약 5.6km
        CourseCondition withFarDestination =
                new CourseCondition(ORIGIN, farDestination, 300, TransportType.WALK, List.of());

        assertTrue(composer.isWithinLegLimit(randomCondition(300), List.of(near)));
        assertFalse(composer.isWithinLegLimit(randomCondition(300), List.of(far)));
        assertFalse(composer.isWithinLegLimit(randomCondition(300), List.of(near, far))); // 카페 → 공원 구간이 김
        assertTrue(composer.isWithinLegLimit(withFarDestination, List.of(near)));         // 도착지까지 먼 건 괜찮다
    }

    @Test
    @DisplayName("거리 비율: 1번째 코스(도보 목표 0.3km)는 가까운 장소가, 3번째 코스(목표 1.4km)는 먼 장소가 항상 앞에 온다")
    void orderForSlot_targetsDifferentDistancePerCourse() {
        PlaceCandidate near = place("near", Category.CAFE, "카페", 0.001);    // 약 0.1km
        PlaceCandidate far = place("far", Category.CAFE, "디저트", 0.012);    // 약 1.3km

        for (int i = 0; i < 30; i++) { // 무작위가 들어가니 여러 번 돌려서 확인
            assertEquals("near", composer.orderForSlot(randomCondition(180), List.of(far, near), 0, 0, 2).get(0).placeId());
            assertEquals("far", composer.orderForSlot(randomCondition(180), List.of(near, far), 2, 0, 2).get(0).placeId());
        }
    }

    @Test
    @DisplayName("거리 비율: 목표 거리는 최대 반경의 15% / 40% / 70% (도보 2km → 0.3 / 0.8 / 1.4km)")
    void targetDistanceKm_byCourseIndex() {
        assertEquals(0.3, composer.targetDistanceKm(randomCondition(180), 0), 0.001);
        assertEquals(0.8, composer.targetDistanceKm(randomCondition(180), 1), 0.001);
        assertEquals(1.4, composer.targetDistanceKm(randomCondition(180), 2), 0.001);
    }

    @Test
    @DisplayName("목표에 비슷하게 맞는 장소끼리는 순서가 바뀌기도 한다 (코스가 매번 같지 않게)")
    void orderForSlot_mixesSimilarDistances() {
        PlaceCandidate a = place("a", Category.CAFE, "카페", 0.001);    // 약 0.1km
        PlaceCandidate b = place("b", Category.CAFE, "디저트", 0.002);  // 약 0.2km

        Set<String> firsts = new java.util.HashSet<>();
        for (int i = 0; i < 30; i++) {
            firsts.add(composer.orderForSlot(randomCondition(180), List.of(a, b), 0, 0, 2).get(0).placeId());
        }
        assertEquals(Set.of("a", "b"), firsts); // 둘 다 1등을 해 본 적이 있다
    }

    @Test
    @DisplayName("목표는 걸러 내는 조건이 아니다: 가까운 장소만 있어도 3번째 코스(먼 목표)가 만들어진다")
    void compose_stillBuildsCourse_whenNoPlaceNearTarget() {
        List<PlaceCandidate> onlyNear = List.of(
                place("1", Category.CAFE, "카페", 0.001),
                place("2", Category.NATURE, "공원", 0.001),
                place("3", Category.SHOPPING, "소품샵", 0.002),
                place("4", Category.FOOD, "한식", 0.002));

        assertEquals(3, composer.compose(randomCondition(180), onlyNear).size());
    }

    // ===================== 한 코스에 넣지 않을 조합 (#170) =====================

    @Test
    @DisplayName("음식/맛집은 한 코스에 1곳만: 한식 → 일식 같은 '밥 두 번' 코스는 없다")
    void compose_foodOnlyOnce() {
        List<PlaceCandidate> candidates = List.of(
                place("1", Category.FOOD, "한식", 0.001),
                place("2", Category.FOOD, "일식", 0.002),
                place("3", Category.FOOD, "중식", 0.003),
                place("4", Category.NATURE, "공원", 0.002));

        List<CoursePlan> courses = composer.compose(randomCondition(300), candidates); // 5시간: 3곳까지 가능

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            assertTrue(countOf(course, Category.FOOD) <= 1, "음식점이 두 번: " + subCategoriesOf(course));
        }
    }

    @Test
    @DisplayName("카페/디저트도 한 코스에 1곳만, 음식점 → 카페 는 함께 들어갈 수 있다")
    void compose_cafeOnlyOnce_butFoodAndCafeTogether() {
        List<PlaceCandidate> candidates = List.of(
                place("1", Category.CAFE, "카페", 0.001),
                place("2", Category.CAFE, "디저트", 0.002),
                place("3", Category.FOOD, "한식", 0.001));

        List<CoursePlan> courses = composer.compose(directCondition(300, Category.FOOD, Category.CAFE), candidates);

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            assertTrue(countOf(course, Category.CAFE) <= 1, "카페가 두 번: " + subCategoriesOf(course));
        }
        assertTrue(courses.stream().anyMatch(c -> countOf(c, Category.FOOD) == 1 && countOf(c, Category.CAFE) == 1),
                "음식점 + 카페 코스가 하나도 없음");
    }

    @Test
    @DisplayName("음식/맛집만 골랐으면 시간이 남아도 1곳짜리 코스다")
    void compose_foodOnly_makesOnePlaceCourse() {
        List<PlaceCandidate> candidates = List.of(
                place("1", Category.FOOD, "한식", 0.001),
                place("2", Category.FOOD, "일식", 0.002));

        List<CoursePlan> courses = composer.compose(directCondition(300, Category.FOOD), candidates);

        assertFalse(courses.isEmpty());
        courses.forEach(course -> assertEquals(1, course.stops().size()));
    }

    @Test
    @DisplayName("같은 브랜드(체인)는 두 번 넣지 않는다: 메가커피 성수역점 → 메가커피 뚝섬점 X")
    void compose_skipsSameBrand() {
        PlaceCandidate megaA = named("1", "메가MGC커피 성수역점", Category.CAFE, "카페", 0.001);
        PlaceCandidate megaB = named("2", "메가MGC커피 뚝섬점", Category.SHOPPING, "소품샵", 0.002); // 대분류가 달라도 같은 브랜드
        PlaceCandidate park = named("3", "성수동구두테마공원", Category.NATURE, "공원", 0.003);

        List<CoursePlan> courses = composer.compose(randomCondition(300), List.of(megaA, megaB, park));

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            assertFalse(course.placeIds().containsAll(Set.of("1", "2")), "같은 브랜드가 두 번: " + course.placeIds());
        }
    }

    @Test
    @DisplayName("브랜드 이름: 마지막 단어가 '점' 으로 끝나면 지점명으로 보고 뗀다")
    void brandOf() {
        assertEquals("메가MGC커피", composer.brandOf("메가MGC커피 성수역점"));
        assertEquals("프랑스루브르바게트", composer.brandOf("프랑스루브르바게트 성수점"));
        assertEquals("성수동구두테마공원", composer.brandOf("성수동구두테마공원"));        // 지점명 없음
        assertEquals("자연과의만남추구 정원", composer.brandOf("자연과의만남추구 정원"));  // '점' 으로 안 끝남
        assertEquals("", composer.brandOf(null));
    }

    private long countOf(CoursePlan course, Category category) {
        return course.stops().stream().filter(stop -> stop.place().category() == category).count();
    }

    private PlaceCandidate named(String id, String name, Category category, String subCategory, double latOffset) {
        return new PlaceCandidate(id, name, category, subCategory, "서울 성동구",
                ORIGIN.latitude() + latOffset, ORIGIN.longitude());
    }

    // ===================== 경로 모드: 도착지 방향 (#170) =====================
    // 도착지는 출발지에서 북쪽으로 0.03도(약 3.3km). 그 사이에 있는 장소가 "가는 길" 이다.

    private static final Coordinate NORTH_DESTINATION = new Coordinate(ORIGIN.latitude() + 0.03, ORIGIN.longitude());

    @Test
    @DisplayName("경로 모드는 도착지가 1km 이상 멀 때만이다")
    void isRouteMode() {
        Coordinate near = new Coordinate(ORIGIN.latitude() + 0.005, ORIGIN.longitude()); // 약 0.56km

        assertTrue(composer.isRouteMode(routeCondition(TransportType.WALK, 240)));
        assertFalse(composer.isRouteMode(new CourseCondition(ORIGIN, near, 240, TransportType.WALK, List.of())));
        assertFalse(composer.isRouteMode(randomCondition(240))); // 도착지 없음
    }

    @Test
    @DisplayName("경로 모드: 가는 길을 많이 벗어난 장소(출발지 반대쪽)는 넣지 않는다")
    void compose_routeMode_skipsPlaceOffTheWay() {
        PlaceCandidate onTheWay = place("on", Category.CAFE, "카페", 0.01);     // 북쪽, 가는 길 위 (우회 0km)
        PlaceCandidate behind = place("behind", Category.NATURE, "공원", -0.01); // 남쪽, 우회 약 2.2km (> 도보 1km)

        List<CoursePlan> courses = composer.compose(routeCondition(TransportType.WALK, 240), List.of(onTheWay, behind));

        assertFalse(courses.isEmpty());
        for (CoursePlan course : courses) {
            assertFalse(course.placeIds().contains("behind"), "가는 길을 벗어난 장소가 들어감: " + course.placeIds());
        }
    }

    @Test
    @DisplayName("경로 모드: 우회 거리도 본다. 출발지에서 가까워도 반대쪽 장소보다 길 위 장소가 앞에 온다")
    void orderForSlot_routeMode_prefersPlaceOnTheWay() {
        PlaceCandidate onTheWay = place("on", Category.CAFE, "카페", 0.015);       // 위치 0.5, 우회 0
        PlaceCandidate behind = place("behind", Category.CAFE, "디저트", -0.004);  // 위치 약 0.1, 우회 약 0.9km

        for (int i = 0; i < 30; i++) {
            // 240분 → 3곳, 2번째 코스의 첫 자리 목표 위치 0.25
            List<PlaceCandidate> ordered =
                    composer.orderForSlot(routeCondition(TransportType.WALK, 240), List.of(behind, onTheWay), 1, 0, 3);
            assertEquals("on", ordered.get(0).placeId());
        }
    }

    @Test
    @DisplayName("경로 모드: 자리마다 목표 위치가 달라 장소가 길을 따라 퍼진다 (첫 자리는 출발지 쪽, 둘째 자리는 도착지 쪽)")
    void orderForSlot_routeMode_spreadsAlongTheWay() {
        PlaceCandidate early = place("early", Category.CAFE, "카페", 0.008);    // 위치 약 0.27
        PlaceCandidate middle = place("middle", Category.NATURE, "공원", 0.015); // 위치 0.5
        PlaceCandidate late = place("late", Category.SHOPPING, "소품샵", 0.022); // 위치 약 0.73
        List<PlaceCandidate> all = List.of(early, middle, late);
        CourseCondition condition = routeCondition(TransportType.WALK, 180); // 2곳 → 목표 0.33 · 0.67

        for (int i = 0; i < 30; i++) {
            assertNotEquals("late", composer.orderForSlot(condition, all, 1, 0, 2).get(0).placeId());
            assertNotEquals("early", composer.orderForSlot(condition, all, 1, 1, 2).get(0).placeId());
        }
    }

    @Test
    @DisplayName("경로 모드 목표 위치: 2곳이면 0.33·0.67, 코스마다 −0.1 / 0 / +0.1, 0 ~ 1 을 넘지 않는다")
    void targetPosition() {
        assertEquals(1.0 / 3, composer.targetPosition(1, 0, 2), 0.001);
        assertEquals(2.0 / 3, composer.targetPosition(1, 1, 2), 0.001);
        assertEquals(1.0 / 3 - 0.1, composer.targetPosition(0, 0, 2), 0.001);
        assertEquals(0.5 + 0.1, composer.targetPosition(2, 0, 1), 0.001);
        assertEquals(0.25, composer.targetPosition(1, 0, 3), 0.001);
        assertTrue(composer.targetPosition(2, 3, 4) <= 1.0);
    }

    @Test
    @DisplayName("경로 위 위치: 출발지 0, 도착지 1, 가운데 0.5")
    void routePosition() {
        CourseCondition condition = routeCondition(TransportType.WALK, 180);

        assertEquals(0.0, composer.routePosition(condition, place("o", Category.CAFE, "카페", 0)), 0.001);
        assertEquals(0.5, composer.routePosition(condition, place("m", Category.CAFE, "카페", 0.015)), 0.001);
        assertEquals(1.0, composer.routePosition(condition, place("d", Category.CAFE, "카페", 0.03)), 0.001);
    }

    @Test
    @DisplayName("경로 모드 방문 순서: 도착지 쪽으로 점점 가까워지는 순 (출발지에서 가까운 순이면 되돌아가는 순서가 나옴)")
    void orderForVisit_routeMode_movesTowardDestination() {
        // side : 북쪽 0.001 + 동쪽 0.013 (출발지에서 약 1.15km, 출발지 옆쪽)
        // ahead: 북쪽 0.010              (출발지에서 약 1.11km, 도착지 쪽)
        PlaceCandidate side = new PlaceCandidate("side", "옆", Category.CAFE, "카페", "서울 성동구",
                ORIGIN.latitude() + 0.001, ORIGIN.longitude() + 0.013);
        PlaceCandidate ahead = place("ahead", Category.NATURE, "공원", 0.010);

        // 출발지에서 가까운 순(④)은 ahead → side: 도착지 쪽으로 갔다가 옆으로 되돌아온다
        assertEquals(List.of("ahead", "side"), idsOf(composer.orderByNearest(ORIGIN, List.of(side, ahead))));
        // 경로 모드는 side → ahead: 도착지 쪽으로 점점 가까워진다
        assertEquals(List.of("side", "ahead"),
                idsOf(composer.orderForVisit(routeCondition(TransportType.TRANSIT, 240), List.of(ahead, side))));
    }

    private CourseCondition routeCondition(TransportType transportType, int availableMinutes) {
        return new CourseCondition(ORIGIN, NORTH_DESTINATION, availableMinutes, transportType, List.of());
    }

    private List<String> idsOf(List<PlaceCandidate> places) {
        return places.stream().map(PlaceCandidate::placeId).toList();
    }

    // ===================== 조건 정리 =====================

    @Test
    @DisplayName("조건의 대분류 중복은 하나로 정리되고, 비어 있으면 랜덤이다")
    void courseCondition_normalizesCategories() {
        CourseCondition duplicated = directCondition(180, Category.CAFE, Category.CAFE, Category.FOOD);
        CourseCondition empty = new CourseCondition(ORIGIN, null, 180, TransportType.WALK, null);

        assertEquals(List.of(Category.CAFE, Category.FOOD), duplicated.categories());
        assertTrue(empty.isRandom());
    }

    // ===================== 도우미 =====================

    private CourseCondition randomCondition(int availableMinutes) {
        return new CourseCondition(ORIGIN, null, availableMinutes, TransportType.WALK, List.of());
    }

    private CourseCondition directCondition(int availableMinutes, Category... categories) {
        return new CourseCondition(ORIGIN, null, availableMinutes, TransportType.WALK, List.of(categories));
    }

    /** 출발지에서 북쪽으로 latOffset 만큼 떨어진 후보 */
    private PlaceCandidate place(String id, Category category, String subCategory, double latOffset) {
        return new PlaceCandidate(id, "장소" + id, category, subCategory, "서울 성동구",
                ORIGIN.latitude() + latOffset, ORIGIN.longitude());
    }

    /** 대분류·세분류가 고루 섞인 후보 8곳 */
    private List<PlaceCandidate> manyCandidates() {
        return List.of(
                place("1", Category.CAFE, "카페", 0.001),       // 40분
                place("2", Category.CAFE, "베이커리", 0.002),   // 40분
                place("3", Category.CAFE, "디저트", 0.003),     // 40분
                place("4", Category.FOOD, "한식", 0.001),       // 40분
                place("5", Category.FOOD, "분식", 0.004),       // 40분
                place("6", Category.FOOD, "일식", 0.002),       // 40분
                place("7", Category.NATURE, "공원", 0.003),     // 40분
                place("8", Category.SHOPPING, "소품샵", 0.005)); // 40분
    }

    private List<String> subCategoriesOf(CoursePlan course) {
        return course.stops().stream().map(CourseStop::place).map(PlaceCandidate::subCategory).toList();
    }
}
