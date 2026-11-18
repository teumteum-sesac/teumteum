package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
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
import com.teumteum.recommend.dto.AlternativePlaceListResponse;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 장소 교체 후보 조회 흐름 테스트.
 * <p>
 * 보관소·RecommendService·후보 고르기(AlternativeFinder)·응답 변환 모두 진짜를 쓴다.
 * 코스 찾기(CourseSearchService)는 이 흐름에서 부르지 않으므로 가짜로 둔다.
 * 묶음은 보관소에 직접 넣어서, "보관한 후보에서 고르는지" 와 404·410 을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceReplaceServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 18, 13, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private CourseSearchService courseSearchService;

    private InMemoryRecommendationStore store;
    private PlaceReplaceService placeReplaceService;

    private final PlaceCandidate cafeA = place("A", "카페", 37.5450, 127.0560);
    private final PlaceCandidate cafeC = place("C", "카페", 37.5452, 127.0570);  // 교체 후보
    private final PlaceCandidate dessert = place("D", "디저트", 37.5451, 127.0565);

    @BeforeEach
    void setUp() {
        store = new InMemoryRecommendationStore();
        placeReplaceService = serviceAt(NOW);
    }

    @Test
    @DisplayName("보관한 후보 중에서 고르고, 바꿀 장소 요약(방문 순서 1부터)과 함께 돌려준다")
    void getAlternatives_ok() {
        save(OffsetDateTime.of(2026, 11, 18, 15, 0, 0, 0, ZoneOffset.ofHours(9)));

        AlternativePlaceListResponse response = placeReplaceService.getAlternatives("rc_1", "A");

        assertEquals("rc_1", response.recommendationId());
        assertEquals("A", response.currentPlace().placeId());
        assertEquals(1, response.currentPlace().visitOrder());
        assertEquals(List.of("C", "D"),   // 같은 세분류(카페) 먼저, 그다음 디저트. A 자신은 빠짐
                response.alternatives().stream().map(a -> a.placeId()).toList());
        assertTrue(response.alternatives().get(0).estimated());
    }

    @Test
    @DisplayName("없는 코스면 404")
    void getAlternatives_courseNotFound() {
        CustomException e = assertThrows(CustomException.class,
                () -> placeReplaceService.getAlternatives("rc_없음", "A"));
        assertEquals(RecommendErrorCode.RECOMMENDATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("코스에 없는 장소 ID 면 404")
    void getAlternatives_placeNotInCourse() {
        save(NOW.plusHours(2));

        CustomException e = assertThrows(CustomException.class,
                () -> placeReplaceService.getAlternatives("rc_1", "C")); // C 는 후보지 코스 장소가 아님
        assertEquals(RecommendErrorCode.RECOMMENDATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("만료된 코스면 410")
    void getAlternatives_expired() {
        save(NOW.plusHours(2));
        PlaceReplaceService twoHoursLater = serviceAt(NOW.plusHours(2));

        CustomException e = assertThrows(CustomException.class,
                () -> twoHoursLater.getAlternatives("rc_1", "A"));
        assertEquals(RecommendErrorCode.RECOMMENDATION_EXPIRED, e.getErrorCode());
    }

    // ===================== 도우미 =====================

    /** 이 시각의 시계를 쓰는 서비스 (같은 보관소) */
    private PlaceReplaceService serviceAt(OffsetDateTime time) {
        RecommendResponseMapper mapper = new RecommendResponseMapper();
        RecommendService recommendService = new RecommendService(courseSearchService, store, mapper,
                Clock.fixed(time.toInstant(), ZoneOffset.ofHours(9)));
        AlternativeFinder finder = new AlternativeFinder(
                new StayTimeCalculator(), new StraightLineTravelCalculator(), new CourseTimeCalculator());
        return new PlaceReplaceService(recommendService, finder, mapper);
    }

    /** 카페 A 한 곳짜리 코스(rc_1)와, 후보 A·C·D 를 가진 묶음(rs_1)을 보관 */
    private void save(OffsetDateTime expiresAt) {
        CourseStop stop = new CourseStop(cafeA, 60, new TravelRoute(5, List.of()));
        CoursePlan plan = new CoursePlan(List.of(stop), null, 65, 5, false);
        RecommendedCourse course = new RecommendedCourse("rc_1", "rs_1", plan, expiresAt);
        RecommendCondition condition = new RecommendCondition(
                new Location("성수역", null, 37.5446, 127.0559), null, "13:00", 180,
                TransportType.WALK, CategoryMode.RANDOM, List.of());
        store.save(new RecommendationSet("rs_1", condition, List.of(course), expiresAt.minusHours(2), expiresAt,
                List.of(cafeA, cafeC, dessert)));
    }

    private PlaceCandidate place(String id, String subCategory, double lat, double lng) {
        return new PlaceCandidate(id, "장소 " + id, CategoryCode.CAFE, subCategory, "서울", lat, lng);
    }
}
