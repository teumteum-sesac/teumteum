package com.teumteum.course.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.teumteum.course.dto.*;
import com.teumteum.course.exception.CourseErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.*;
import com.teumteum.place.service.PlaceLatestService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.*;

class CourseLatestServiceTest {
    final CourseService courses = mock(CourseService.class);
    final PlaceLatestService places = mock(PlaceLatestService.class);
    CourseLatestService service = new CourseLatestService(courses, places, Duration.ofSeconds(1));

    @AfterEach
    void close() { service.close(); }

    @Test
    void checksOwnershipBeforeReadingSourceOrCallingExternalService() {
        var error = new CustomException(CourseErrorCode.COURSE_NOT_FOUND);
        given(courses.getCourse(7L, 42L)).willThrow(error);
        assertThatThrownBy(() -> service.getLatest(7L, 42L)).isSameAs(error);
        verifyNoInteractions(places);
    }

    @Test
    void returnsOrderedSuccessNotFoundAndFailureWithoutMutatingSnapshot() {
        var snapshot = saved(3);
        given(courses.getCourse(7L, 42L)).willReturn(snapshot);
        var first = source(1); var second = source(2); var third = source(3);
        given(places.source(1L)).willReturn(first);
        given(places.source(2L)).willReturn(second);
        given(places.source(3L)).willReturn(third);
        given(places.lookup(first)).willReturn(PlaceLatestLookup.success(latest()));
        given(places.lookup(second)).willReturn(PlaceLatestLookup.notFound());
        given(places.lookup(third)).willThrow(new IllegalStateException("provider failure"));

        var result = service.getLatest(7L, 42L);
        assertThat(result.places()).extracting(CourseLatestPlaceResponse::status)
                .containsExactly("SUCCESS", "NOT_FOUND", "FAILED");
        assertThat(result.places()).extracting(CourseLatestPlaceResponse::coursePlaceId)
                .containsExactly("101", "102", "103");
        assertThat(result.places().getFirst().place().placeName()).isEqualTo("최신 이름");
        assertThat(result.places().get(1).place()).isNull();
        assertThat(result.places().get(1).errorCode()).isEqualTo("PLACE_INFORMATION_NOT_FOUND");
        assertThat(result.places().get(2).fetchedAt()).isNull();
        assertThat(result.places().get(2).errorCode()).isEqualTo("PLACE_LOOKUP_FAILED");
        assertThat(snapshot).isEqualTo(saved(3));
        verify(courses).getCourse(7L, 42L);
        verifyNoMoreInteractions(courses);
    }

    @Test
    void respectsWholeRequestDeadlineAndKeepsOtherCompletedResults() {
        service.close();
        service = new CourseLatestService(courses, places, Duration.ofMillis(200));
        given(courses.getCourse(7L, 42L)).willReturn(saved(2));
        var slow = source(1); var fast = source(2);
        given(places.source(1L)).willReturn(slow);
        given(places.source(2L)).willReturn(fast);
        given(places.lookup(slow)).willAnswer(invocation -> { Thread.sleep(5_000); return PlaceLatestLookup.notFound(); });
        given(places.lookup(fast)).willReturn(PlaceLatestLookup.success(latest()));
        long start = System.nanoTime();
        var response = service.getLatest(7L, 42L);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(response.places()).extracting(CourseLatestPlaceResponse::status).containsExactly("FAILED", "SUCCESS");
    }

    @Test
    void sourceDatabaseFailureIsNotReportedAsExternalProviderFailure() {
        given(courses.getCourse(7L, 42L)).willReturn(saved(1));
        var failure = new org.springframework.dao.DataAccessResourceFailureException("test DB failure");
        given(places.source(1L)).willThrow(failure);
        assertThatThrownBy(() -> service.getLatest(7L, 42L)).isSameAs(failure);
        verify(places, never()).lookup(any());
    }

    static LatestPlaceResponse latest() {
        return new LatestPlaceResponse("최신 이름", null, null, "최신 주소", 38.0, 128.0, null, null, null, null, null);
    }
    static PlaceLatestSource source(int id) { return new PlaceLatestSource("external-" + id, "검색 단서", 37.0, 127.0); }
    static CourseResponse saved(int count) {
        var list = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new CoursePlaceResponse("10" + i, "" + i, i, "저장 이름", "CAFE", "카페", "저장 주소",
                        BigDecimal.valueOf(37), BigDecimal.valueOf(127), "stored-image", "stored-description", "stored-hours", 50, 10, null, null)).toList();
        return new CourseResponse("42", "rec", "저장 코스명", "2026-11-18T13:00:00+09:00", null, null,
                "WALK", "13:00", 60, 10, 0, true, list, List.of());
    }
}
