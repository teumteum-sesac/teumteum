package com.teumteum.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.teumteum.course.domain.Course;
import com.teumteum.course.domain.CoursePlace;
import com.teumteum.course.domain.CourseRouteLeg;
import com.teumteum.course.dto.CourseListResponse;
import com.teumteum.course.exception.CourseErrorCode;
import com.teumteum.course.repository.CoursePlaceCount;
import com.teumteum.course.repository.CoursePlaceRepository;
import com.teumteum.course.repository.CourseRepository;
import com.teumteum.course.repository.CourseRouteLegRepository;
import com.teumteum.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import static org.mockito.Mockito.never;

// 실제 DB 없이 회원별 조회 조건, 집계 결과 연결, 페이지 응답을 검증한다.
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CoursePlaceRepository coursePlaceRepository;
    @Mock
    private CourseRouteLegRepository courseRouteLegRepository;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(courseRepository, coursePlaceRepository, courseRouteLegRepository);
    }

    @Test
    void returnsMemberCoursesWithCountsAndFixedSort() {
        Course course = mock(Course.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 12, 10, 0);
        given(course.getCourseId()).willReturn(42L);
        given(course.getSourceRecommendationId()).willReturn("rec-123");
        given(course.getCourseName()).willReturn("카페 → 공원");
        given(course.getTotalMinutes()).willReturn((short) 130);
        given(course.getCreatedAt()).willReturn(createdAt);
        given(courseRepository.findByMemberId(eq(7L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(course), PageRequest.of(0, 1), 2));
        CoursePlaceCount count = mock(CoursePlaceCount.class);
        given(count.getCourseId()).willReturn(42L);
        given(count.getPlaceCount()).willReturn(2L);
        given(coursePlaceRepository.countPlacesByCourseIds(List.of(42L)))
                .willReturn(List.of(count));

        CourseListResponse response = courseService.getCourses(7L, 0, 1);

        assertThat(response.content()).hasSize(1);
        var summary = response.content().getFirst();
        assertThat(summary.courseId()).isEqualTo("42");
        assertThat(summary.sourceRecommendationId()).isEqualTo("rec-123");
        assertThat(summary.courseName()).isEqualTo("카페 → 공원");
        assertThat(summary.placeCount()).isEqualTo(2);
        assertThat(summary.totalMinutes()).isEqualTo(130);
        assertThat(OffsetDateTime.parse(summary.createdAt()))
                .isEqualTo(createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime());
        assertThat(response.totalElements()).isEqualTo(2L);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(courseRepository).findByMemberId(eq(7L), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("courseId")));
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 0", "3, 25, 2"})
    void emptyPagePreservesTotalsAndSkipsCountQuery(int page, long total, int totalPages) {
        given(courseRepository.findByMemberId(eq(7L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(page, 20), total));

        CourseListResponse response = courseService.getCourses(7L, page, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(page);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(total);
        assertThat(response.totalPages()).isEqualTo(totalPages);
        assertThat(response.hasNext()).isFalse();
        verifyNoInteractions(coursePlaceRepository);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 51"})
    void rejectsInvalidPaginationBeforeQuerying(int page, int size) {
        assertThatThrownBy(() -> courseService.getCourses(7L, page, size))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(courseRepository, coursePlaceRepository);
    }

    @Test
    void rejectsMissingMemberBeforeQuerying() {
        assertThatThrownBy(() -> courseService.getCourses(null, 0, 20))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(courseRepository, coursePlaceRepository);
    }

    @Test
    void rejectsCourseWithoutStoredPlaces() {
        Course course = mock(Course.class);
        given(course.getCourseId()).willReturn(42L);
        given(courseRepository.findByMemberId(eq(7L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(course), PageRequest.of(0, 20), 1));
        given(coursePlaceRepository.countPlacesByCourseIds(List.of(42L))).willReturn(List.of());

        assertThatThrownBy(() -> courseService.getCourses(7L, 0, 20))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsOtherMembersCourse() {
        // 회원 7이 코스 42를 조회했지만,
        // 두 ID가 모두 일치하는 데이터는 없다고 설정한다.
        given(courseRepository.findByCourseIdAndMemberId(42L, 7L))
                .willReturn(Optional.empty());

        // 상세 조회가 정상 응답 대신 예외를 발생시키는지 확인한다.
        assertThatThrownBy(() -> courseService.getCourse(7L, 42L))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(CourseErrorCode.COURSE_NOT_FOUND);
                });

        // 소유권 확인에 실패하면 장소나 경로는 조회하면 안 된다.
        verifyNoInteractions(
                coursePlaceRepository,
                courseRouteLegRepository
        );
    }

    @Test
    void returnsOwnedCourseDetail() {
        // 1. DB에서 조회했다고 가정할 코스 객체를 만든다.
        Course course = mock(Course.class);

        given(course.getCourseId()).willReturn(42L);
        given(course.getSourceRecommendationId()).willReturn("rec-123");
        given(course.getCourseName()).willReturn("카페 외출");
        given(course.getCreatedAt())
                .willReturn(LocalDateTime.of(2026, 11, 12, 10, 0));

        given(course.getOriginName()).willReturn("출발지");
        given(course.getOriginLatitude()).willReturn(new BigDecimal("37.5650"));
        given(course.getOriginLongitude()).willReturn(new BigDecimal("126.9770"));

        given(course.getTransportType()).willReturn(Course.TransportType.WALK);
        given(course.getStartTime()).willReturn(LocalTime.of(13, 0));
        given(course.getTotalMinutes()).willReturn((short) 60);
        given(course.getTotalTravelMinutes()).willReturn((short) 10);
        given(course.getFinalTravelMinutes()).willReturn((short) 0);

        // 회원 7이 코스 42의 소유자라면 이 코스를 반환하도록 설정한다.
        given(courseRepository.findByCourseIdAndMemberId(42L, 7L))
                .willReturn(Optional.of(course));

        // 2. 코스에 저장된 장소 한 개를 준비한다.
        CoursePlace place = mock(CoursePlace.class);

        given(place.getCoursePlaceId()).willReturn(100L);
        given(place.getPlaceId()).willReturn(200L);
        given(place.getVisitOrder()).willReturn((short) 1);
        given(place.getPlaceName()).willReturn("저장 당시 카페");
        given(place.getStayMinutes()).willReturn((short) 50);
        given(place.getTravelMinutes()).willReturn((short) 10);

        given(coursePlaceRepository
                .findByCourse_CourseIdOrderByVisitOrderAsc(42L))
                .willReturn(List.of(place));

        // 3. 출발지에서 첫 장소까지의 저장 경로를 준비한다.
        CourseRouteLeg leg = mock(CourseRouteLeg.class);

        given(leg.getFromVisitOrder()).willReturn((short) 0);
        given(leg.getToVisitOrder()).willReturn((short) 1);
        given(leg.getPathType()).willReturn(CourseRouteLeg.PathType.STRAIGHT);
        given(leg.getCoordinates()).willReturn(List.of(
                new CourseRouteLeg.Coordinate(
                        new BigDecimal("37.5650"),
                        new BigDecimal("126.9770")
                ),
                new CourseRouteLeg.Coordinate(
                        new BigDecimal("37.5665"),
                        new BigDecimal("126.9780")
                )
        ));

        given(courseRouteLegRepository
                .findByCourse_CourseIdOrderByFromVisitOrderAsc(42L))
                .willReturn(List.of(leg));

        // 4. 상세 조회를 실제로 실행한다.
        var response = courseService.getCourse(7L, 42L);

        // 5. 코스 기본 정보가 응답 DTO로 변환됐는지 확인한다.
        assertThat(response.courseId()).isEqualTo("42");
        assertThat(response.sourceRecommendationId()).isEqualTo("rec-123");
        assertThat(response.courseName()).isEqualTo("카페 외출");
        assertThat(response.transportType()).isEqualTo("WALK");
        assertThat(response.startTime()).isEqualTo("13:00");
        assertThat(response.totalMinutes()).isEqualTo(60);
        assertThat(response.totalTravelMinutes()).isEqualTo(10);
        assertThat(response.finalTravelMinutes()).isZero();

        // 도착지는 설정하지 않았으므로 null이어야 한다.
        assertThat(response.destination()).isNull();
        assertThat(response.origin().name()).isEqualTo("출발지");

        // 6. 저장 장소가 응답에 포함됐는지 확인한다.
        assertThat(response.places()).hasSize(1);

        var savedPlace = response.places().getFirst();
        assertThat(savedPlace.coursePlaceId()).isEqualTo("100");
        assertThat(savedPlace.placeId()).isEqualTo("200");
        assertThat(savedPlace.visitOrder()).isEqualTo(1);
        assertThat(savedPlace.placeName()).isEqualTo("저장 당시 카페");
        assertThat(savedPlace.stayMinutes()).isEqualTo(50);
        assertThat(savedPlace.travelMinutes()).isEqualTo(10);

        // 7. 저장 경로와 좌표가 응답에 포함됐는지 확인한다.
        assertThat(response.routeLegs()).hasSize(1);

        var savedLeg = response.routeLegs().getFirst();
        assertThat(savedLeg.fromVisitOrder()).isZero();
        assertThat(savedLeg.toVisitOrder()).isEqualTo(1);
        assertThat(savedLeg.pathType()).isEqualTo("STRAIGHT");
        assertThat(savedLeg.coordinates()).hasSize(2);
        assertThat(savedLeg.coordinates().getFirst().latitude())
                .isEqualByComparingTo("37.5650");
        assertThat(savedLeg.coordinates().getLast().longitude())
                .isEqualByComparingTo("126.9780");
    }

    @Test
    void deletesOwnedCourse() {
        // 1. 삭제 대상 코스를 준비한다.
        Course course = mock(Course.class);
        given(course.getCourseId()).willReturn(42L);

        // 2. 회원 7의 코스 42가 조회되는 상황을 설정한다.
        given(courseRepository.findByCourseIdAndMemberId(42L, 7L))
                .willReturn(Optional.of(course));

        // 3. 삭제 메서드를 실행한다.
        var response = courseService.deleteCourse(7L, 42L);

        // 4. 조회한 코스를 Repository에 삭제 요청했는지 확인한다.
        verify(courseRepository).delete(course);

        // 5. 삭제 결과가 올바른지 확인한다.
        assertThat(response.courseId()).isEqualTo("42");
        assertThat(response.deleted()).isTrue();
    }

    @Test
    void rejectsDeletingOtherMembersCourse() {
        // 회원 7과 코스 42가 모두 일치하는 데이터가 없는 상황이다.
        // 코스가 없거나, 다른 회원 소유일 때 이런 결과가 나온다.
        given(courseRepository.findByCourseIdAndMemberId(42L, 7L))
                .willReturn(Optional.empty());

        // 삭제를 시도하면 COURSE_NOT_FOUND 예외가 발생해야 한다.
        assertThatThrownBy(() -> courseService.deleteCourse(7L, 42L))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(CourseErrorCode.COURSE_NOT_FOUND);
                });

        // 소유권 확인에 실패했으므로 삭제 메서드는 호출되면 안 된다.
        verify(courseRepository, never()).delete(any(Course.class));
    }
}
