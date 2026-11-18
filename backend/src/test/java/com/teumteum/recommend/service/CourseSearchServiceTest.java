package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.NearbyPlaceResult;
import com.teumteum.place.exception.PlaceErrorCode;
import com.teumteum.place.service.NearbyPlaceService;
import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseSearchResult;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 반경 넓히기·후보 변환 테스트.
 * <p>
 * 장소 검색(NearbyPlaceService)과 코스 조합(CourseComposer)은 둘 다 가짜(@Mock)로 바꾼다.
 * 코스 조합 규칙은 CourseComposerTest 에서 이미 확인했으므로, 여기서는
 * "몇 km 까지 넓히는지, 언제 멈추는지, 장소 결과를 어떻게 바꾸는지" 만 본다.
 */
@ExtendWith(MockitoExtension.class)
class CourseSearchServiceTest {

    private static final Coordinate ORIGIN = new Coordinate(37.5446, 127.0559);

    @Mock
    private NearbyPlaceService nearbyPlaceService;
    @Mock
    private CourseComposer courseComposer;

    @InjectMocks
    private CourseSearchService courseSearchService;

    /** 코스 조합에 실제로 넘어간 후보 목록을 붙잡는다 (@Captor 로 만들면 제네릭 타입 경고가 없다) */
    @Captor
    private ArgumentCaptor<List<PlaceCandidate>> candidatesCaptor;

    private final CourseCondition randomCondition =
            new CourseCondition(ORIGIN, null, 180, TransportType.WALK, List.of());

    // ===================== 반경 넓히기 =====================

    @Test
    @DisplayName("1km 에서 코스 3개가 나오면 더 넓히지 않는다")
    void findCourses_stopsAtFirstRadius_whenThreeFound() {
        givenNearbyResults();
        given(courseComposer.compose(eq(randomCondition), anyList(), anyList()))
                .willReturn(List.of(course("1"), course("2"), course("3")));

        List<CoursePlan> courses = courseSearchService.findCourses(randomCondition).plans();

        assertEquals(3, courses.size());
        verify(nearbyPlaceService).findCandidates(anyDouble(), anyDouble(), any(), eq(1_000));
        verify(nearbyPlaceService, never()).findCandidates(anyDouble(), anyDouble(), any(), eq(2_000));
    }

    @Test
    @DisplayName("1km 에서 1개뿐이면 2km 로 넓혀 모자란 2개를 채운다")
    void findCourses_widensRadius_untilThree() {
        givenNearbyResults();
        given(courseComposer.compose(eq(randomCondition), anyList(), anyList()))
                .willReturn(List.of(course("1")), List.of(course("2"), course("3"))); // 1번째 호출, 2번째 호출

        List<CoursePlan> courses = courseSearchService.findCourses(randomCondition).plans();

        assertEquals(3, courses.size());
        verify(nearbyPlaceService).findCandidates(anyDouble(), anyDouble(), any(), eq(2_000));
        verify(nearbyPlaceService, never()).findCandidates(anyDouble(), anyDouble(), any(), eq(3_000));
    }

    @Test
    @DisplayName("도보는 2km 까지만 넓히고, 그래도 코스가 없으면 빈 목록이다 (오류 아님)")
    void findCourses_walk_stopsAt2km() {
        givenNearbyResults();
        given(courseComposer.compose(eq(randomCondition), anyList(), anyList())).willReturn(List.of());

        List<CoursePlan> courses = courseSearchService.findCourses(randomCondition).plans();

        assertTrue(courses.isEmpty());
        verify(nearbyPlaceService).findCandidates(anyDouble(), anyDouble(), any(), eq(1_000));
        verify(nearbyPlaceService).findCandidates(anyDouble(), anyDouble(), any(), eq(2_000));
        verify(nearbyPlaceService, never()).findCandidates(anyDouble(), anyDouble(), any(), eq(3_000));
        verify(nearbyPlaceService, never()).findCandidates(anyDouble(), anyDouble(), any(), eq(5_000));
    }

    @Test
    @DisplayName("대중교통은 5km 까지 넓히고, 그래도 코스가 없으면 빈 목록이다 (오류 아님)")
    void findCourses_transit_widensTo5km() {
        CourseCondition transit = new CourseCondition(ORIGIN, null, 180, TransportType.TRANSIT, List.of());
        givenNearbyResults();
        given(courseComposer.compose(eq(transit), anyList(), anyList())).willReturn(List.of());

        List<CoursePlan> courses = courseSearchService.findCourses(transit).plans();

        assertTrue(courses.isEmpty());
        for (int radius : List.of(1_000, 2_000, 3_000, 5_000)) {
            verify(nearbyPlaceService).findCandidates(anyDouble(), anyDouble(), any(), eq(radius));
        }
    }

    // ===================== 장소 제외 (다시 추천) =====================

    @Test
    @DisplayName("제외할 장소는 후보에서 빼고 코스 조합에 넘긴다")
    void findCourses_excludesPlaces() {
        given(nearbyPlaceService.findCandidates(anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(List.of(nearby("101", "서울 성동구 왕십리로 1", null),
                        nearby("102", "서울 성동구 왕십리로 2", null)));
        given(courseComposer.compose(eq(randomCondition), anyList(), anyList()))
                .willReturn(List.of(course("1"), course("2"), course("3")));

        CourseSearchResult result = courseSearchService.findCourses(randomCondition, Set.of("101"));

        verify(courseComposer).compose(eq(randomCondition), candidatesCaptor.capture(), anyList());
        List<PlaceCandidate> passed = candidatesCaptor.getValue();
        assertEquals(1, passed.size());
        assertEquals("102", passed.get(0).placeId()); // 코스 조합에서는 101 이 빠졌다
        // 교체 후보로 보관할 목록에는 101 도 들어 있다 (제외는 새 코스 만들 때만)
        assertEquals(List.of("101", "102"), result.candidates().stream().map(PlaceCandidate::placeId).toList());
    }

    // ===================== 후보 보관 (장소 교체용) =====================

    @Test
    @DisplayName("반경을 넓히며 받은 후보를 장소 ID 로 중복 없이, 처음 받은 순서대로 돌려준다")
    void findCourses_returnsCandidatesWithoutDuplicates() {
        given(nearbyPlaceService.findCandidates(anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(List.of(nearby("101", "도로 1", null)))                              // 1km
                .willReturn(List.of(nearby("101", "도로 1", null), nearby("102", "도로 2", null))); // 2km (101 또 나옴)
        given(courseComposer.compose(eq(randomCondition), anyList(), anyList()))
                .willReturn(List.of(course("1")), List.of(course("2"), course("3")));

        CourseSearchResult result = courseSearchService.findCourses(randomCondition);

        assertEquals(List.of("101", "102"), result.candidates().stream().map(PlaceCandidate::placeId).toList());
        assertEquals(3, result.plans().size());
    }

    // ===================== 장소 검색 실패 =====================

    @Test
    @DisplayName("처음부터 장소 검색이 실패하면 장소 오류를 그대로 전달한다")
    void findCourses_throws_whenSearchFailsWithNothingFound() {
        CustomException placeError = new CustomException(PlaceErrorCode.KAKAO_API_ERROR);
        given(nearbyPlaceService.findCandidates(anyDouble(), anyDouble(), any(), anyInt())).willThrow(placeError);

        CustomException thrown = assertThrows(CustomException.class,
                () -> courseSearchService.findCourses(randomCondition));

        assertSame(placeError, thrown);
    }

    @Test
    @DisplayName("넓히다가 장소 검색이 실패하면 이미 찾은 코스로 응답한다")
    void findCourses_returnsFound_whenLaterSearchFails() {
        given(nearbyPlaceService.findCandidates(anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(List.of(nearby("101", "서울 성동구 왕십리로 1", "서울 성동구 성수동 1")))
                .willThrow(new CustomException(PlaceErrorCode.KAKAO_API_RATE_LIMIT)); // 2km 에서 실패
        given(courseComposer.compose(eq(randomCondition), anyList(), anyList())).willReturn(List.of(course("1")));

        List<CoursePlan> courses = courseSearchService.findCourses(randomCondition).plans();

        assertEquals(1, courses.size());
    }

    // ===================== 변환 =====================

    @Test
    @DisplayName("랜덤이면 장소 검색에 빈 대분류를 넘기고, 직접 선택이면 고른 대분류를 그대로 넘긴다")
    void searchCategories() {
        CourseCondition direct = new CourseCondition(ORIGIN, null, 180, TransportType.WALK,
                List.of(Category.CAFE, Category.SPORTS));

        assertTrue(courseSearchService.searchCategories(randomCondition).isEmpty());
        assertEquals(EnumSet.of(Category.CAFE, Category.SPORTS),
                courseSearchService.searchCategories(direct));
    }

    @Test
    @DisplayName("장소 결과를 후보로 바꿀 때 대분류는 그대로, 주소는 도로명을 먼저 쓰고, 카카오맵 링크도 담는다")
    void toPlaceCandidate_convertsFields() {
        PlaceCandidate candidate = courseSearchService.toPlaceCandidate(
                nearby("101", "서울 성동구 왕십리로 1", "서울 성동구 성수동 1"));

        assertEquals("101", candidate.placeId());
        assertEquals(Category.SPORTS, candidate.category());
        assertEquals("당구", candidate.subCategory());
        assertEquals("서울 성동구 왕십리로 1", candidate.address());
        assertEquals(37.545, candidate.latitude());
        assertEquals("https://place.map.kakao.com/101", candidate.placeUrl());
    }

    @Test
    @DisplayName("도로명 주소가 없으면 지번 주소를 쓴다")
    void toPlaceCandidate_usesJibunAddress_whenNoRoadAddress() {
        PlaceCandidate candidate = courseSearchService.toPlaceCandidate(nearby("101", null, "서울 성동구 성수동 1"));

        assertEquals("서울 성동구 성수동 1", candidate.address());
    }

    @Test
    @DisplayName("반경 단계는 1·2·3·5km 다")
    void radiusSteps() {
        assertEquals(List.of(1_000, 2_000, 3_000, 5_000), CourseSearchService.RADIUS_STEPS);
    }

    @Test
    @DisplayName("이동수단별 반경 단계: 도보 1·2km, 대중교통·차량 1·2·3·5km")
    void radiusStepsFor_eachTransport() {
        assertEquals(List.of(1_000, 2_000), courseSearchService.radiusStepsFor(conditionOf(TransportType.WALK)));
        assertEquals(List.of(1_000, 2_000, 3_000, 5_000), courseSearchService.radiusStepsFor(conditionOf(TransportType.TRANSIT)));
        assertEquals(List.of(1_000, 2_000, 3_000, 5_000), courseSearchService.radiusStepsFor(conditionOf(TransportType.CAR)));
    }

    private CourseCondition conditionOf(TransportType transportType) {
        return new CourseCondition(ORIGIN, null, 180, transportType, List.of());
    }

    // ===================== 경로 모드: 출발지·중간·도착지 검색 (#170) =====================

    private static final Coordinate DESTINATION = new Coordinate(37.5746, 127.0559); // 출발지 북쪽 약 3.3km
    private final CourseCondition routeCondition =
            new CourseCondition(ORIGIN, DESTINATION, 240, TransportType.TRANSIT, List.of());

    @Test
    @DisplayName("경로 모드가 아니면 출발지 한 곳만 검색한다")
    void searchCenters_originOnly() {
        given(courseComposer.isRouteMode(randomCondition)).willReturn(false);

        assertEquals(List.of(ORIGIN), courseSearchService.searchCenters(randomCondition));
    }

    @Test
    @DisplayName("경로 모드면 출발지·중간 지점·도착지 세 곳 주변을 검색한다")
    void findCourses_routeMode_searchesThreeCenters() {
        given(courseComposer.isRouteMode(routeCondition)).willReturn(true);
        givenNearbyResults();
        given(courseComposer.compose(eq(routeCondition), anyList(), anyList()))
                .willReturn(List.of(course("1"), course("2"), course("3")));

        courseSearchService.findCourses(routeCondition);

        verify(nearbyPlaceService).findCandidates(eq(37.5446), eq(127.0559), any(), eq(1_000)); // 출발지
        verify(nearbyPlaceService).findCandidates(eq(37.5596), eq(127.0559), any(), eq(1_000)); // 중간
        verify(nearbyPlaceService).findCandidates(eq(37.5746), eq(127.0559), any(), eq(1_000)); // 도착지
    }

    @Test
    @DisplayName("경로 모드에서 여러 지점에 같은 장소가 나오면 코스 조합에는 한 번만 넘긴다")
    void findCourses_routeMode_mergesDuplicates() {
        given(courseComposer.isRouteMode(routeCondition)).willReturn(true);
        givenNearbyResults(); // 세 지점 모두 같은 장소 101 을 돌려준다
        given(courseComposer.compose(eq(routeCondition), candidatesCaptor.capture(), anyList()))
                .willReturn(List.of(course("1"), course("2"), course("3")));

        courseSearchService.findCourses(routeCondition);

        assertEquals(1, candidatesCaptor.getValue().size());
    }

    // ===================== 도우미 =====================

    /** 어떤 반경으로 물어봐도 후보 1곳을 돌려주게 한다 */
    private void givenNearbyResults() {
        given(nearbyPlaceService.findCandidates(anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(List.of(nearby("101", "서울 성동구 왕십리로 1", "서울 성동구 성수동 1")));
    }

    private NearbyPlaceResult nearby(String id, String roadAddress, String address) {
        return new NearbyPlaceResult(id, "당구장 A", Category.SPORTS, "당구",
                address, roadAddress, 37.545, 127.056, 120, "https://place.map.kakao.com/" + id);
    }

    /** 장소 1곳짜리 가짜 코스 (장소 ID 로 서로 구분) */
    private CoursePlan course(String placeId) {
        PlaceCandidate place = new PlaceCandidate(placeId, "장소" + placeId, Category.CAFE, "카페",
                "서울", 37.545, 127.056);
        CourseStop stop = new CourseStop(place, 60, new TravelRoute(10, List.of()));
        return new CoursePlan(List.of(stop), null, 70, 10, false);
    }
}
