package com.teumteum.course.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teumteum.course.dto.CourseListResponse;
import com.teumteum.course.dto.CourseCoordinateResponse;
import com.teumteum.course.dto.CourseLocationResponse;
import com.teumteum.course.dto.CoursePlaceResponse;
import com.teumteum.course.dto.CourseResponse;
import com.teumteum.course.dto.CourseRouteLegResponse;
import com.teumteum.course.dto.CourseSummaryResponse;
import com.teumteum.course.exception.CourseErrorCode;
import com.teumteum.course.service.CourseService;
import com.teumteum.global.auth.LoginMemberArgumentResolver;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import com.teumteum.course.dto.CourseDeleteResponse;

// 서버와 DB 없이 실제 요청 매핑, 회원 ID 해석, 공통 응답 및 예외 처리를 검증한다.
class CourseControllerTest {
    private MockMvc mockMvc;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseController(courseService))
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsCoursesWithDefaultPagination() throws Exception {
        var summary = new CourseSummaryResponse("42", "rec-123", "카페 → 공원", 2, 130,
                "2026-11-12T10:00:00+09:00");
        given(courseService.getCourses(7L, 0, 20)).willReturn(
                new CourseListResponse(List.of(summary), 0, 20, 1L, 1, false));

        mockMvc.perform(get("/api/courses").header("X-Member-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].courseId").value("42"))
                .andExpect(jsonPath("$.data.content[0].placeCount").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        verify(courseService).getCourses(7L, 0, 20);
    }

    @Test
    void passesExplicitPaginationAndReturnsEmptyPage() throws Exception {
        given(courseService.getCourses(7L, 3, 10)).willReturn(
                new CourseListResponse(List.of(), 3, 10, 25L, 3, false));

        mockMvc.perform(get("/api/courses").header("X-Member-Id", "7")
                        .param("page", "3").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(25))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void missingMemberReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(courseService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void invalidMemberReturnsUnauthorized(String memberId) throws Exception {
        mockMvc.perform(get("/api/courses").header("X-Member-Id", memberId))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(courseService);
    }

    @ParameterizedTest
    @CsvSource({"page, abc", "size, 2147483648"})
    void invalidNumberReturnsBadRequest(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/courses").header("X-Member-Id", "7").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(courseService);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 51"})
    void serviceValidationErrorUsesCommonResponse(int page, int size) throws Exception {
        given(courseService.getCourses(7L, page, size))
                .willThrow(new CustomException(CommonErrorCode.INVALID_REQUEST));

        mockMvc.perform(get("/api/courses").header("X-Member-Id", "7")
                        .param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void detailNotFoundReturns404() throws Exception {
        // 1. Service가 코스를 찾지 못한 상황을 설정한다.
        // 실제 DB 조회는 하지 않는다. 소유권 판단 자체는 CourseServiceTest에서 검증한다.
        // 없거나 다른 회원의 코스면 Service는 같은 COURSE_NOT_FOUND 예외를 던진다.
        given(courseService.getCourse(7L, 42L))
                .willThrow(new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

        // 2. 회원 7이 코스 42를 상세 조회하는 HTTP 요청을 흉내 낸다.
        // 주소의 42는 @PathVariable로, 임시 헤더의 7은 @LoginMember로 전달된다.
        mockMvc.perform(get("/api/courses/42")
                        .header("X-Member-Id", "7"))
                // 3. GlobalExceptionHandler가 예외를 HTTP 404로 변환하는지 확인한다.
                .andExpect(status().isNotFound())
                // 4. 공통 응답의 실패 여부와 오류 코드가 올바른지 확인한다.
                // $.error.code는 응답 JSON의 error 객체 안에 있는 code 필드를 가리킨다.
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));

        // 5. Controller가 회원 ID와 코스 ID를 올바른 순서로 Service에 전달했는지 확인한다.
        verify(courseService).getCourse(7L, 42L);
    }

    @Test
    void returnsCourseDetailWithSavedPlacesAndRoute() throws Exception {
        // Service가 반환할 저장본을 준비한다. Controller는 이 값을 바꾸지 않고 JSON으로 전달한다.
        var origin = new CourseLocationResponse("출발지", null,
                new BigDecimal("37.5650"), new BigDecimal("126.9770"));
        var place = new CoursePlaceResponse("100", "200", 1, "저장 당시 카페", "CAFE", "카페",
                null, new BigDecimal("37.5665"), new BigDecimal("126.9780"),
                null, null, null, 50, 10, null);
        var leg = new CourseRouteLegResponse(0, 1, "STRAIGHT", List.of(
                new CourseCoordinateResponse(origin.latitude(), origin.longitude()),
                new CourseCoordinateResponse(place.latitude(), place.longitude())
        ));
        var response = new CourseResponse("42", "rec-123", "카페 외출",
                "2026-11-12T10:00:00+09:00", origin, null, "WALK", "13:00",
                60, 10, 0, true, List.of(place), List.of(leg));
        given(courseService.getCourse(7L, 42L)).willReturn(response);

        // 회원 ID는 임시 인증 헤더, 코스 ID는 주소에서 가져온다.
        mockMvc.perform(get("/api/courses/42").header("X-Member-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courseId").value("42"))
                .andExpect(jsonPath("$.data.courseName").value("카페 외출"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-12T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.origin.latitude").value(37.5650))
                .andExpect(jsonPath("$.data.destination").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.startTime").value("13:00"))
                .andExpect(jsonPath("$.data.totalMinutes").value(60))
                .andExpect(jsonPath("$.data.estimated").value(true))
                .andExpect(jsonPath("$.data.places.length()").value(1))
                .andExpect(jsonPath("$.data.places[0].coursePlaceId").value("100"))
                .andExpect(jsonPath("$.data.places[0].placeName").value("저장 당시 카페"))
                .andExpect(jsonPath("$.data.routeLegs[0].pathType").value("STRAIGHT"))
                .andExpect(jsonPath("$.data.routeLegs[0].coordinates.length()").value(2))
                .andExpect(jsonPath("$.data.routeLegs[0].coordinates[1].longitude").value(126.9780));
        verify(courseService).getCourse(7L, 42L);
    }

    @Test
    void detailWithoutMemberReturns401() throws Exception {
        // 인증 정보가 없으면 기존 Resolver가 Service 호출 전에 요청을 거절한다.
        mockMvc.perform(get("/api/courses/42"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(courseService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    void malformedCourseIdReturns400(String courseId) throws Exception {
        // 숫자가 아니거나 Long 범위를 넘는 주소 값은 Spring의 타입 변환 단계에서 거절한다.
        mockMvc.perform(get("/api/courses/" + courseId).header("X-Member-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(courseService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void nonPositiveCourseIdReturns400(long courseId) throws Exception {
        // 정수로 변환되지만 유효하지 않은 ID는 Service가 검사한다.
        // 여기서는 그 예외가 Controller 밖에서 공통 400 응답으로 바뀌는지 확인한다.
        given(courseService.getCourse(7L, courseId))
                .willThrow(new CustomException(CommonErrorCode.INVALID_REQUEST));

        mockMvc.perform(get("/api/courses/" + courseId).header("X-Member-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(courseService).getCourse(7L, courseId);
    }

    @Test
    void deletesCourseAndReturns200() throws Exception {
        // Service가 정상 삭제 결과를 반환하도록 설정한다.
        given(courseService.deleteCourse(7L, 42L))
                .willReturn(new CourseDeleteResponse("42", true));

        // 회원 7이 코스 42를 삭제하는 요청을 보낸다.
        mockMvc.perform(delete("/api/courses/42")
                        .header("X-Member-Id", "7"))
                // HTTP 상태와 공통 응답 형식을 확인한다.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courseId").value("42"))
                .andExpect(jsonPath("$.data.deleted").value(true));

        // 회원 ID와 코스 ID가 올바르게 전달됐는지 확인한다.
        verify(courseService).deleteCourse(7L, 42L);
    }

    @Test
    void deleteWithoutMemberReturns401() throws Exception {
        // 회원 ID 헤더 없이 삭제를 요청한다.
        mockMvc.perform(delete("/api/courses/42"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 회원 확인에 실패했으므로 Service를 호출하면 안 된다.
        verifyNoInteractions(courseService);
    }

    @Test
    void deleteNotFoundReturns404() throws Exception {
        // 본인 소유의 코스를 찾지 못해 Service가 예외를 던지는 상황이다.
        given(courseService.deleteCourse(7L, 42L))
                .willThrow(new CustomException(
                        CourseErrorCode.COURSE_NOT_FOUND
                ));

        // 회원 7이 코스 42의 삭제를 요청한다.
        mockMvc.perform(delete("/api/courses/42")
                        .header("X-Member-Id", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("COURSE_NOT_FOUND"));

        // 요청값이 Service에 올바르게 전달됐는지도 확인한다.
        verify(courseService).deleteCourse(7L, 42L);
    }

    @Test
    void deleteWithInvalidIdReturns400() throws Exception {
        // 코스 ID는 Long 타입인데 주소에 문자열 abc를 넣는다.
        mockMvc.perform(delete("/api/courses/abc")
                        .header("X-Member-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        // Spring이 ID 변환에 실패하므로 Service는 호출되지 않아야 한다.
        verifyNoInteractions(courseService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void deleteWithNonPositiveIdReturns400(long courseId) throws Exception {
        // 0과 음수로 각각 삭제를 요청한다.
        mockMvc.perform(delete("/api/courses/" + courseId)
                        .header("X-Member-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        // @Positive 검증에서 차단되므로 Service는 호출되지 않아야 한다.
        verifyNoInteractions(courseService);
    }
}
