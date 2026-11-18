package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceAlternative;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 장소 교체 후보 고르기·시간 계산 테스트.
 * <p>
 * 계산기 3개(체류·직선거리·총 시간)는 외부 API 가 없는 순수 계산이라 가짜 대신 진짜를 쓴다.
 * 기대값도 같은 계산기로 구해서, 속도 상수가 바뀌어도 테스트를 고치지 않아도 되게 한다.
 * <pre>
 * 출발지(성수역) ──5분──▶ [0] 카페 A ──10분──▶ [1] 공원 B      (도보, 이용 시간 180분)
 * </pre>
 */
class AlternativeFinderTest {

    private static final Coordinate ORIGIN = new Coordinate(37.5446, 127.0559);
    private static final Coordinate DESTINATION = new Coordinate(37.5500, 127.0700);

    private final StayTimeCalculator stayTimeCalculator = new StayTimeCalculator();
    private final StraightLineTravelCalculator straightLine = new StraightLineTravelCalculator();
    private final AlternativeFinder finder =
            new AlternativeFinder(stayTimeCalculator, straightLine, new CourseTimeCalculator());

    private final PlaceCandidate cafeA = place("A", CategoryCode.CAFE, "카페", 37.5450, 127.0560);
    private final PlaceCandidate parkB = place("B", CategoryCode.NATURE, "공원", 37.5460, 127.0600);

    // ===================== 고르기 =====================

    @Test
    @DisplayName("같은 세분류를 가까운 순으로 먼저, 그다음 같은 대분류의 다른 세분류. 다른 대분류와 코스 장소는 빠진다")
    void picksSameSubCategoryFirst_thenSameCategory() {
        PlaceCandidate nearCafe = place("c1", CategoryCode.CAFE, "카페", 37.5451, 127.0570);
        PlaceCandidate farCafe = place("c2", CategoryCode.CAFE, "카페", 37.5550, 127.0700);
        PlaceCandidate bakery = place("d1", CategoryCode.CAFE, "베이커리", 37.5452, 127.0565); // 제일 가깝지만 세분류가 다름
        PlaceCandidate food = place("f1", CategoryCode.FOOD, "한식", 37.5451, 127.0561);      // 다른 대분류

        List<PlaceAlternative> result = finder.find(condition(180, null), plan(), 0,
                List.of(farCafe, food, cafeA, bakery, parkB, nearCafe));

        assertEquals(List.of("c1", "c2", "d1"), placeIds(result));
    }

    @Test
    @DisplayName("후보는 최대 5곳이다")
    void limitsToFive() {
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            candidates.add(place("c" + i, CategoryCode.CAFE, "카페", 37.5450 + i * 0.001, 127.0560));
        }

        List<PlaceAlternative> result = finder.find(condition(180, null), plan(), 0, candidates);

        assertEquals(List.of("c0", "c1", "c2", "c3", "c4"), placeIds(result)); // 가까운 5곳
    }

    @Test
    @DisplayName("가까운 순은 앞 지점과 뒤 지점 거리의 합으로 정한다 (앞 지점에만 가까운 곳이 먼저 오지 않음)")
    void ordersByDistanceToBothNeighbors() {
        // 카페 A(앞 지점: 출발지, 뒤 지점: 공원 B)를 바꿀 때
        PlaceCandidate behindOrigin = place("back", CategoryCode.CAFE, "카페", 37.5440, 127.0550); // 출발지에 더 가깝지만 B 와 멀다
        PlaceCandidate onTheWay = place("way", CategoryCode.CAFE, "카페", 37.5455, 127.0585);       // 출발지와 B 사이

        List<PlaceAlternative> result = finder.find(condition(180, null), plan(), 0, List.of(behindOrigin, onTheWay));

        assertEquals(List.of("way", "back"), placeIds(result));
    }

    @Test
    @DisplayName("고를 후보가 없으면 빈 목록이다")
    void returnsEmpty_whenNoCandidates() {
        assertTrue(finder.find(condition(180, null), plan(), 0, List.of(cafeA, parkB)).isEmpty());
    }

    // ===================== 시간 계산 =====================

    @Test
    @DisplayName("첫 장소 교체: 출발지→후보, 후보→다음 장소 이동과 체류를 지금 값과 비교해 diff 를 낸다")
    void calculatesDiff_forFirstPlace() {
        PlaceCandidate bakery = place("d1", CategoryCode.CAFE, "베이커리", 37.5452, 127.0565);

        PlaceAlternative alternative = finder.find(condition(180, null), plan(), 0, List.of(bakery)).get(0);

        int travelIn = walk(ORIGIN, bakery);
        int travelOut = walk(at(bakery), parkB);
        int expected = (travelIn + stay("베이커리") + travelOut) - (5 + stay("카페") + 10);
        assertEquals(stay("베이커리"), alternative.stayMinutes());
        assertEquals(travelIn, alternative.travelMinutes());   // 화면의 "도보 N분" 은 앞 지점 → 후보
        assertEquals(expected, alternative.diffMinutes());
    }

    @Test
    @DisplayName("마지막 장소 교체 + 도착지 있음: 후보→도착지 이동을 넣고 지금의 마지막→도착지 이동을 뺀다")
    void calculatesDiff_forLastPlaceWithDestination() {
        PlaceCandidate trail = place("t1", CategoryCode.NATURE, "산책로", 37.5470, 127.0620);

        PlaceAlternative alternative = finder.find(condition(180, DESTINATION), planWithFinalTravel(7), 1,
                List.of(trail)).get(0);

        int expected = (walk(at(cafeA), trail) + stay("산책로") + walk(at(trail), DESTINATION))
                - (10 + stay("공원") + 7);
        assertEquals(expected, alternative.diffMinutes());
    }

    @Test
    @DisplayName("마지막 장소 교체 + 도착지 없음: 뒤 지점이 없으니 후보까지 이동과 체류만 비교한다")
    void calculatesDiff_forLastPlaceWithoutDestination() {
        PlaceCandidate trail = place("t1", CategoryCode.NATURE, "산책로", 37.5470, 127.0620);

        PlaceAlternative alternative = finder.find(condition(180, null), plan(), 1, List.of(trail)).get(0);

        int expected = (walk(at(cafeA), trail) + stay("산책로")) - (10 + stay("공원"));
        assertEquals(expected, alternative.diffMinutes());
    }

    @Test
    @DisplayName("바꾼 뒤 총 시간이 이용 시간을 넘으면 exceedsAvailableTime true, 같거나 적으면 false")
    void marksExceeding() {
        int total = plan().totalMinutes();
        PlaceCandidate farCafe = place("far", CategoryCode.CAFE, "카페", 37.5600, 127.0800);     // 멀어서 늘어남
        PlaceCandidate sameSpot = place("same", CategoryCode.CAFE, "카페", 37.5450, 127.0560);  // A 와 같은 자리

        List<PlaceAlternative> result = finder.find(condition(total, null), plan(), 0, List.of(farCafe, sameSpot));

        PlaceAlternative same = result.get(0);
        PlaceAlternative far = result.get(1);
        assertTrue(far.diffMinutes() > 0);
        assertTrue(far.exceedsAvailableTime());
        assertTrue(same.diffMinutes() <= 0);    // 이동 추정이 지금 값(5·10분)보다 짧거나 같다
        assertFalse(same.exceedsAvailableTime());
    }

    // ===================== 도우미 =====================

    /** 출발지 ─5분─▶ 카페 A(60분) ─10분─▶ 공원 B(60분), 도착지 없음 */
    private CoursePlan plan() {
        return planWithFinalTravel(0);
    }

    /** 위 코스에 마지막 장소 → 도착지 이동(분)을 붙인 것. 0 이면 도착지 없음 */
    private CoursePlan planWithFinalTravel(int finalTravelMinutes) {
        List<CourseStop> stops = List.of(
                new CourseStop(cafeA, stay("카페"), new TravelRoute(5, List.of())),
                new CourseStop(parkB, stay("공원"), new TravelRoute(10, List.of())));
        TravelRoute finalTravel = finalTravelMinutes == 0 ? null : new TravelRoute(finalTravelMinutes, List.of());
        int total = 5 + stay("카페") + 10 + stay("공원") + finalTravelMinutes;
        return new CoursePlan(stops, finalTravel, total, 15 + finalTravelMinutes, false);
    }

    private CourseCondition condition(int availableMinutes, Coordinate destination) {
        return new CourseCondition(ORIGIN, destination, availableMinutes, TransportType.WALK, List.of());
    }

    private PlaceCandidate place(String id, CategoryCode category, String subCategory, double lat, double lng) {
        return new PlaceCandidate(id, "장소 " + id, category, subCategory, "서울", lat, lng);
    }

    private int stay(String subCategory) {
        return stayTimeCalculator.calculate(subCategory);
    }

    private int walk(Coordinate from, PlaceCandidate to) {
        return walk(from, at(to));
    }

    private int walk(Coordinate from, Coordinate to) {
        return straightLine.calculateTravelMinutes(TransportType.WALK,
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private Coordinate at(PlaceCandidate place) {
        return new Coordinate(place.latitude(), place.longitude());
    }

    private List<String> placeIds(List<PlaceAlternative> alternatives) {
        return alternatives.stream().map(a -> a.place().placeId()).toList();
    }
}
