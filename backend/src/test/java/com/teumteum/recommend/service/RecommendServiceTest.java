package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.exception.PlaceErrorCode;
import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.dto.LocationRequest;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.dto.RecommendRequest;
import com.teumteum.recommend.dto.RecommendResponse;
import com.teumteum.recommend.exception.RecommendErrorCode;
import com.teumteum.recommend.repository.InMemoryRecommendationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 추천 생성 서비스 테스트.
 * <p>
 * - 코스 찾기(CourseSearchService)는 가짜: 장소 검색·이동시간 API 를 부르지 않는다
 * - 보관소는 진짜 메모리 보관소: "응답의 ID 로 다시 찾을 수 있는지" 를 실제로 확인한다
 * - 시계는 오후 1시에 고정: 만료 시각이 정확히 2시간 뒤인지 본다
 */
@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 17, 13, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private CourseSearchService courseSearchService;

    private InMemoryRecommendationStore store;
    private RecommendService recommendService;

    @BeforeEach
    void setUp() {
        store = new InMemoryRecommendationStore();
        recommendService = new RecommendService(courseSearchService, store, new RecommendResponseMapper(),
                Clock.fixed(NOW.toInstant(), ZoneOffset.ofHours(9)));
    }

    @Test
    @DisplayName("묶음·코스에 rs_/rc_ ID 를 붙이고, 만료 시각은 정확히 2시간 뒤다")
    void recommend_assignsIdsAndExpiresAt() {
        given(courseSearchService.findCourses(any())).willReturn(List.of(plan("101"), plan("102")));

        RecommendResponse response = recommendService.recommend(request(CategoryMode.RANDOM, List.of()));

        assertTrue(response.recommendationSetId().startsWith("rs_"));
        assertTrue(response.courses().get(0).recommendationId().startsWith("rc_"));
        assertNotEquals(response.courses().get(0).recommendationId(), response.courses().get(1).recommendationId());
        assertEquals(NOW.plusHours(2), response.expiresAt());
    }

    @Test
    @DisplayName("만료 시각은 초 단위로 잘라서 소수점 초가 붙지 않는다")
    void recommend_expiresAtTruncatedToSeconds() {
        // 13:00:07.503 에 요청한 것처럼 시계를 맞춘다
        OffsetDateTime withNanos = NOW.plusSeconds(7).plusNanos(503_033_800);
        RecommendService service = new RecommendService(courseSearchService, store, new RecommendResponseMapper(),
                Clock.fixed(withNanos.toInstant(), ZoneOffset.ofHours(9)));
        given(courseSearchService.findCourses(any())).willReturn(List.of());

        RecommendResponse response = service.recommend(request(CategoryMode.RANDOM, List.of()));

        assertEquals(NOW.plusSeconds(7).plusHours(2), response.expiresAt());   // 15:00:07 (소수점 없음)
        assertEquals(0, response.expiresAt().getNano());
    }

    @Test
    @DisplayName("응답의 묶음 ID·코스 ID 로 보관소에서 다시 찾을 수 있다 (상세·다시 추천·저장 대비)")
    void recommend_savesToStore() {
        given(courseSearchService.findCourses(any())).willReturn(List.of(plan("101")));

        RecommendResponse response = recommendService.recommend(request(CategoryMode.RANDOM, List.of()));

        assertTrue(store.findSet(response.recommendationSetId()).isPresent());
        String courseId = response.courses().get(0).recommendationId();
        assertEquals(response.recommendationSetId(), store.findCourse(courseId).orElseThrow().recommendationSetId());
    }

    @Test
    @DisplayName("코스가 없어도 오류 없이 빈 목록으로 응답하고 묶음은 보관한다 (REQ-027)")
    void recommend_emptyCourses() {
        given(courseSearchService.findCourses(any())).willReturn(List.of());

        RecommendResponse response = recommendService.recommend(request(CategoryMode.RANDOM, List.of()));

        assertTrue(response.courses().isEmpty());
        assertTrue(store.findSet(response.recommendationSetId()).isPresent());
    }

    @Test
    @DisplayName("코스 찾기에는 정리된 조건이 넘어간다 (직접 선택 + 중복 대분류 → 중복 제거)")
    void recommend_passesNormalizedCondition() {
        given(courseSearchService.findCourses(any())).willReturn(List.of());

        recommendService.recommend(request(CategoryMode.DIRECT, List.of(CategoryCode.CAFE, CategoryCode.CAFE)));

        // ArgumentCaptor: 가짜에게 "실제로 무슨 값이 넘어왔는지" 붙잡아서 꺼내 본다
        ArgumentCaptor<CourseCondition> captor = ArgumentCaptor.forClass(CourseCondition.class);
        verify(courseSearchService).findCourses(captor.capture());
        assertEquals(List.of(CategoryCode.CAFE), captor.getValue().categories());
        assertEquals(180, captor.getValue().availableMinutes());
    }

    @Test
    @DisplayName("장소 검색 오류(502·503)는 바꾸지 않고 그대로 전달한다")
    void recommend_propagatesPlaceError() {
        given(courseSearchService.findCourses(any())).willThrow(new CustomException(PlaceErrorCode.KAKAO_API_ERROR));

        assertThrows(CustomException.class,
                () -> recommendService.recommend(request(CategoryMode.RANDOM, List.of())));
    }

    // ===================== 코스 상세 =====================

    @Test
    @DisplayName("추천받은 코스 ID 로 상세를 조회하면 묶음의 조건(출발지·이동수단)까지 담아 준다")
    void getCourseDetail_ok() {
        given(courseSearchService.findCourses(any())).willReturn(List.of(plan("101")));
        RecommendResponse created = recommendService.recommend(request(CategoryMode.RANDOM, List.of()));
        String courseId = created.courses().get(0).recommendationId();

        RecommendCourseDetailResponse detail = recommendService.getCourseDetail(courseId);

        assertEquals(courseId, detail.recommendationId());
        assertEquals(created.recommendationSetId(), detail.recommendationSetId());
        assertEquals("성수역", detail.origin().name());
        assertEquals(TransportType.WALK, detail.transportType());
        assertEquals(1, detail.routeLegs().size());
    }

    @Test
    @DisplayName("없는 코스 ID 면 404 RECOMMENDATION_NOT_FOUND")
    void getCourseDetail_notFound() {
        CustomException e = assertThrows(CustomException.class,
                () -> recommendService.getCourseDetail("rc_없는아이디"));

        assertEquals(RecommendErrorCode.RECOMMENDATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("2시간이 지난 코스면 410 RECOMMENDATION_EXPIRED")
    void getCourseDetail_expired() {
        given(courseSearchService.findCourses(any())).willReturn(List.of(plan("101")));
        String courseId = recommendService.recommend(request(CategoryMode.RANDOM, List.of()))
                .courses().get(0).recommendationId();

        // 같은 보관소를 쓰고, 시계만 정확히 2시간 뒤(만료 시각)로 맞춘 서비스
        RecommendService twoHoursLater = new RecommendService(courseSearchService, store, new RecommendResponseMapper(),
                Clock.fixed(NOW.plusHours(2).toInstant(), ZoneOffset.ofHours(9)));

        CustomException e = assertThrows(CustomException.class, () -> twoHoursLater.getCourseDetail(courseId));
        assertEquals(RecommendErrorCode.RECOMMENDATION_EXPIRED, e.getErrorCode());
    }

    // ===================== 도우미 =====================

    private RecommendRequest request(CategoryMode mode, List<CategoryCode> categories) {
        LocationRequest origin = new LocationRequest("성수역", null, 37.5446, 127.0559);
        return new RecommendRequest(origin, null, "13:00", 180, TransportType.WALK, mode, categories);
    }

    private CoursePlan plan(String placeId) {
        PlaceCandidate place = new PlaceCandidate(placeId, "장소" + placeId, CategoryCode.CAFE, "카페",
                "서울", 37.545, 127.056);
        CourseStop stop = new CourseStop(place, 60, new TravelRoute(10, List.of()));
        return new CoursePlan(List.of(stop), null, 70, 10, false);
    }
}
