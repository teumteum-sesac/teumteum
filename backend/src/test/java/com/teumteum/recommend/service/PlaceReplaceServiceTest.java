package com.teumteum.recommend.service;

import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.global.common.category.Category;
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
import com.teumteum.recommend.dto.AlternativePlaceListResponse;
import com.teumteum.recommend.dto.PlaceReplaceRequest;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.exception.RecommendErrorCode;
import com.teumteum.recommend.repository.InMemoryRecommendationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 장소 교체 흐름 테스트 (후보 조회 6-1 + 교체 확정 6-2).
 * <p>
 * 보관소·RecommendService·후보 고르기·시간 계산·응답 변환은 진짜를 쓴다.
 * 이동시간 API(TravelTimeService)만 가짜로 바꿔서 "몇 번 부르는지" 와 "결과 시간" 을 정한다.
 * 묶음은 보관소에 직접 넣는다.
 * <pre>
 * 코스 rc_1: 출발지(성수역) ─5분─▶ 카페 A(40분) ─10분─▶ 공원 B(40분)    총 95분, 이용 시간 180분
 * 보관한 후보: A, B, 카페 C, 디저트 D, 산책로 E
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class PlaceReplaceServiceTest {

    /**
     * 지금 시각 기준. 날짜를 고정하면 그 날짜가 지난 뒤 보관소(실제 시계로 만료 정리)가 새로 넣은 코스를 바로 지워 테스트가 깨진다.
     */
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.ofHours(9)).truncatedTo(ChronoUnit.SECONDS);
    private static final OffsetDateTime EXPIRES_AT = NOW.plusHours(2);
    private static final Location DESTINATION = new Location("서울숲", null, 37.5444, 127.0374);

    @Mock
    private CourseSearchService courseSearchService;
    @Mock
    private TravelTimeService travelTimeService;

    private InMemoryRecommendationStore store;
    private PlaceReplaceService placeReplaceService;

    private final PlaceCandidate cafeA = place("A", Category.CAFE, "카페", 37.5450, 127.0560);
    private final PlaceCandidate parkB = place("B", Category.NATURE, "공원", 37.5460, 127.0600);
    private final PlaceCandidate cafeC = place("C", Category.CAFE, "카페", 37.5452, 127.0570);
    private final PlaceCandidate dessertD = place("D", Category.CAFE, "디저트", 37.5451, 127.0565);
    private final PlaceCandidate trailE = place("E", Category.NATURE, "산책로", 37.5465, 127.0610);

    @BeforeEach
    void setUp() {
        store = new InMemoryRecommendationStore();
        placeReplaceService = serviceAt(NOW);
    }

    // ===================== 후보 조회 (6-1) =====================

    @Test
    @DisplayName("후보 조회: 보관한 후보 중에서 고르고, 바꿀 장소 요약(방문 순서 1부터)과 함께 돌려준다")
    void getAlternatives_ok() {
        save(null);

        AlternativePlaceListResponse response = placeReplaceService.getAlternatives("rc_1", "A");

        assertEquals("rc_1", response.recommendationId());
        assertEquals("A", response.currentPlace().placeId());
        assertEquals(1, response.currentPlace().visitOrder());
        assertEquals(List.of("C", "D"),   // 같은 세분류(카페) 먼저, 그다음 디저트. A 자신·다른 대분류는 빠짐
                response.alternatives().stream().map(a -> a.placeId()).toList());
        assertTrue(response.alternatives().get(0).estimated());
    }

    @Test
    @DisplayName("후보 조회: 없는 코스면 404")
    void getAlternatives_courseNotFound() {
        CustomException e = assertThrows(CustomException.class,
                () -> placeReplaceService.getAlternatives("rc_없음", "A"));
        assertEquals(RecommendErrorCode.RECOMMENDATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("후보 조회: 코스에 없는 장소 ID 면 404")
    void getAlternatives_placeNotInCourse() {
        save(null);

        CustomException e = assertThrows(CustomException.class,
                () -> placeReplaceService.getAlternatives("rc_1", "C")); // C 는 후보지 코스 장소가 아님
        assertEquals(RecommendErrorCode.RECOMMENDATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("후보 조회: 만료된 코스면 410")
    void getAlternatives_expired() {
        save(null);

        CustomException e = assertThrows(CustomException.class,
                () -> serviceAt(EXPIRES_AT).getAlternatives("rc_1", "A"));
        assertEquals(RecommendErrorCode.RECOMMENDATION_EXPIRED, e.getErrorCode());
    }

    // ===================== 교체 (6-2) =====================

    @Test
    @DisplayName("교체: 첫 장소를 바꾸면 앞(출발지→C)·뒤(C→B) 두 구간만 API 로 다시 계산하고 새 ID 로 보관한다")
    void replace_firstPlace() {
        save(null);
        givenEveryRouteTakes(7);

        RecommendCourseDetailResponse response = placeReplaceService.replace("rc_1", request("A", "C"));

        verify(travelTimeService, times(2)).findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertTrue(response.recommendationId().startsWith("rc_"));
        assertNotEquals("rc_1", response.recommendationId());                // 새 버전
        assertEquals("rs_1", response.recommendationSetId());                // 묶음은 같음
        assertEquals(EXPIRES_AT, response.expiresAt());                      // 만료 시각도 같음
        assertEquals(List.of("C", "B"), response.places().stream().map(p -> p.placeId()).toList());
        assertEquals(7, response.places().get(1).travelMinutes());          // B 로 가는 구간도 바뀜
        assertEquals(7 + 40 + 7 + 40, response.totalMinutes());
    }

    @Test
    @DisplayName("교체: 원래 코스는 그대로 남고, 새 ID 로 상세를 다시 조회할 수 있다")
    void replace_keepsOriginal() {
        save(null);
        givenEveryRouteTakes(7);

        String newId = placeReplaceService.replace("rc_1", request("A", "C")).recommendationId();

        RecommendedCourse original = store.findCourse("rc_1").orElseThrow();
        assertEquals("A", original.plan().stops().get(0).place().placeId());
        assertEquals("C", store.findCourse(newId).orElseThrow().plan().stops().get(0).place().placeId());
    }

    @Test
    @DisplayName("교체: 마지막 장소를 바꾸고 도착지가 있으면 앞(A→E)과 도착지(E→서울숲) 구간을 다시 계산한다")
    void replace_lastPlace_withDestination() {
        save(DESTINATION);
        givenEveryRouteTakes(7);

        RecommendCourseDetailResponse response = placeReplaceService.replace("rc_1", request("B", "E"));

        verify(travelTimeService, times(2)).findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertEquals(7, response.finalTravelMinutes());
        // 출발지→A 5분·A 40분은 그대로, A→E 7분 + 산책로 40분 + E→서울숲 7분
        assertEquals(5 + 40 + 7 + 40 + 7, response.totalMinutes());
    }

    @Test
    @DisplayName("교체: 새 장소가 교체 후보에 없으면 400 이고 이동시간 API 는 부르지 않는다")
    void replace_notAmongAlternatives() {
        save(null);

        CustomException e = assertThrows(CustomException.class,
                () -> placeReplaceService.replace("rc_1", request("A", "E"))); // E 는 다른 대분류라 A 의 후보가 아님
        assertEquals(CommonErrorCode.INVALID_REQUEST, e.getErrorCode());
        verifyNoInteractions(travelTimeService);
    }

    @Test
    @DisplayName("교체: 실제 이동시간으로 이용 시간을 넘으면 409 이고 아무것도 보관하지 않는다")
    void replace_exceedsAvailableTime() {
        save(null);
        givenEveryRouteTakes(60); // 60 + 40 + 60 + 40 = 200분 > 180분

        CustomException e = assertThrows(CustomException.class,
                () -> placeReplaceService.replace("rc_1", request("A", "C")));

        assertEquals(RecommendErrorCode.AVAILABLE_TIME_EXCEEDED, e.getErrorCode());
        assertEquals("A", store.findCourse("rc_1").orElseThrow().plan().stops().get(0).place().placeId());
    }

    @Test
    @DisplayName("교체: 바꿀 장소가 코스에 없으면 404")
    void replace_targetNotInCourse() {
        save(null);

        CustomException e = assertThrows(CustomException.class,
                () -> placeReplaceService.replace("rc_1", request("C", "D")));
        assertEquals(RecommendErrorCode.RECOMMENDATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("교체: 만료된 코스면 410")
    void replace_expired() {
        save(null);

        CustomException e = assertThrows(CustomException.class,
                () -> serviceAt(EXPIRES_AT).replace("rc_1", request("A", "C")));
        assertEquals(RecommendErrorCode.RECOMMENDATION_EXPIRED, e.getErrorCode());
    }

    // ===================== 도우미 =====================

    /** 이 시각의 시계를 쓰는 서비스 (같은 보관소) */
    private PlaceReplaceService serviceAt(OffsetDateTime time) {
        RecommendResponseMapper mapper = new RecommendResponseMapper();
        RecommendService recommendService = new RecommendService(courseSearchService, store, mapper,
                Clock.fixed(time.toInstant(), ZoneOffset.ofHours(9)));
        StayTimeCalculator stayTimeCalculator = new StayTimeCalculator();
        CourseTimeCalculator courseTimeCalculator = new CourseTimeCalculator();
        AlternativeFinder finder = new AlternativeFinder(
                stayTimeCalculator, new StraightLineTravelCalculator(), courseTimeCalculator);
        return new PlaceReplaceService(recommendService, finder, mapper,
                travelTimeService, stayTimeCalculator, courseTimeCalculator);
    }

    /** 이동시간 API 가 어떤 구간이든 이 시간(분)을 돌려주게 한다 */
    private void givenEveryRouteTakes(int minutes) {
        given(travelTimeService.findRoute(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(new TravelRoute(minutes, List.of()));
    }

    /**
     * 코스 rc_1 (카페 A → 공원 B) 과 후보 A~E 를 가진 묶음 rs_1 을 보관.
     *
     * @param destination 도착지. 없으면 null
     */
    private void save(Location destination) {
        TravelRoute finalTravel = (destination == null) ? null : new TravelRoute(8, List.of());
        CoursePlan plan = new CourseTimeCalculator().toCoursePlan(List.of(
                new CourseStop(cafeA, 40, new TravelRoute(5, List.of())),
                new CourseStop(parkB, 40, new TravelRoute(10, List.of()))), finalTravel);
        RecommendedCourse course = new RecommendedCourse("rc_1", "rs_1", plan, EXPIRES_AT);
        RecommendCondition condition = new RecommendCondition(
                new Location("성수역", null, 37.5446, 127.0559), destination, "13:00", 180,
                TransportType.WALK, CategoryMode.RANDOM, List.of());
        store.save(new RecommendationSet("rs_1", condition, List.of(course), NOW, EXPIRES_AT,
                List.of(cafeA, parkB, cafeC, dessertD, trailE)));
    }

    private PlaceReplaceRequest request(String targetPlaceId, String newPlaceId) {
        return new PlaceReplaceRequest(targetPlaceId, newPlaceId);
    }

    private PlaceCandidate place(String id, Category category, String subCategory, double lat, double lng) {
        return new PlaceCandidate(id, "장소 " + id, category, subCategory, "서울", lat, lng);
    }
}
