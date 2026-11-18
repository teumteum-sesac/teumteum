package com.teumteum.course.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.teumteum.course.dto.*;
import com.teumteum.course.service.CourseLatestService;
import com.teumteum.global.auth.LoginMemberArgumentResolver;
import com.teumteum.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CourseLatestControllerTest {
    final CourseLatestService service = mock(CourseLatestService.class);
    MockMvc mvc;
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CourseLatestController(service))
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test
    void returnsPartialFailureWith200AndNoStoreHeader() throws Exception {
        given(service.getLatest(7L, 42L)).willReturn(new CourseLatestPlacesResponse("42", OffsetDateTime.now(), List.of(
                new CourseLatestPlaceResponse("101", "1", 1, "FAILED", null, null, "PLACE_LOOKUP_FAILED", "최신 정보를 불러오지 못했어요."))));
        mvc.perform(get("/api/courses/42/places/latest")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null, List.of())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.places[0].status").value("FAILED"));
    }
    @Test
    void rejectsAnonymousAndInvalidIds() throws Exception {
        mvc.perform(get("/api/courses/42/places/latest")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses/not-a-number/places/latest")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null, List.of())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
