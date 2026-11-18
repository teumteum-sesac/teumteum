package com.teumteum.recommend.controller;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.exception.GlobalExceptionHandler;
import com.teumteum.place.exception.PlaceErrorCode;
import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.dto.AlternativePlaceListResponse;
import com.teumteum.recommend.dto.AlternativePlaceResponse;
import com.teumteum.recommend.dto.CurrentPlaceResponse;
import com.teumteum.recommend.dto.LocationResponse;
import com.teumteum.recommend.dto.PlaceReplaceRequest;
import com.teumteum.recommend.dto.RecommendConditionResponse;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.dto.RecommendResponse;
import com.teumteum.recommend.exception.RecommendErrorCode;
import com.teumteum.recommend.service.PlaceReplaceService;
import com.teumteum.recommend.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추천 생성 API 테스트. 서버·DB 없이 요청 주소, 요청 검증(400), 공통 응답 형식만 확인한다.
 * (CourseControllerTest 와 같은 방식: standaloneSetup + GlobalExceptionHandler)
 */
class RecommendControllerTest {

    private MockMvc mockMvc;
    private RecommendService recommendService;
    private PlaceReplaceService placeReplaceService;

    @BeforeEach
    void setUp() {
        recommendService = mock(RecommendService.class);
        placeReplaceService = mock(PlaceReplaceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendController(recommendService, placeReplaceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("올바른 요청이면 200 과 ApiResponse 로 감싼 추천 결과를 돌려준다")
    void recommend_ok() throws Exception {
        given(recommendService.recommend(any())).willReturn(emptyResponse());

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recommendationSetId").value("rs_1"))
                .andExpect(jsonPath("$.data.courses").isEmpty());
    }

    @Test
    @DisplayName("이용 시간이 0분이면 400 INVALID_REQUEST 이고 서비스는 부르지 않는다")
    void recommend_badRequest_whenZeroMinutes() throws Exception {
        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"availableMinutes\": 180", "\"availableMinutes\": 0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(recommendService);
    }

    @Test
    @DisplayName("출발지가 없으면 400 INVALID_REQUEST 다")
    void recommend_badRequest_whenNoOrigin() throws Exception {
        String body = """
                { "startTime": "13:00", "availableMinutes": 180, "transportType": "WALK", "categoryMode": "RANDOM" }
                """;

        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("장소 검색 외부 API 가 실패하면 장소 오류 코드(502)가 그대로 나간다")
    void recommend_placeError() throws Exception {
        given(recommendService.recommend(any())).willThrow(new CustomException(PlaceErrorCode.KAKAO_API_ERROR));

        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("KAKAO_API_ERROR"));
    }

    // ===================== 다시 추천 =====================

    @Test
    @DisplayName("다시 추천: 요청 본문 없이 주소의 묶음 ID 와 요청 IP 를 서비스에 넘기고 200 으로 새 결과를 돌려준다")
    void regenerate_ok() throws Exception {
        given(recommendService.regenerate("rs_1", "203.0.113.7")).willReturn(emptyResponse());

        mockMvc.perform(post("/api/recommendations/rs_1/regenerate")
                        .with(request -> {           // 요청 IP 를 정해서 보낸다 (안 정하면 127.0.0.1)
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recommendationSetId").value("rs_1"));
    }

    @Test
    @DisplayName("다시 추천: 만료된 묶음이면 410 RECOMMENDATION_EXPIRED")
    void regenerate_expired() throws Exception {
        given(recommendService.regenerate(any(), any()))
                .willThrow(new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED));

        mockMvc.perform(post("/api/recommendations/rs_old/regenerate"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("RECOMMENDATION_EXPIRED"));
    }

    @Test
    @DisplayName("다시 추천: 너무 자주 누르면 429 TOO_MANY_REQUESTS")
    void regenerate_tooManyRequests() throws Exception {
        given(recommendService.regenerate(any(), any()))
                .willThrow(new CustomException(RecommendErrorCode.TOO_MANY_REQUESTS));

        mockMvc.perform(post("/api/recommendations/rs_1/regenerate"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    // ===================== 코스 상세 =====================

    @Test
    @DisplayName("코스 상세: 주소의 코스 ID 를 서비스에 넘기고 200 으로 상세를 돌려준다")
    void getCourseDetail_ok() throws Exception {
        given(recommendService.getCourseDetail("rc_1")).willReturn(detailResponse());

        mockMvc.perform(get("/api/recommendations/courses/rc_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recommendationId").value("rc_1"))
                .andExpect(jsonPath("$.data.routeLegs").isArray());
    }

    @Test
    @DisplayName("코스 상세: 없는 ID 면 404 RECOMMENDATION_NOT_FOUND")
    void getCourseDetail_notFound() throws Exception {
        given(recommendService.getCourseDetail(any()))
                .willThrow(new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND));

        mockMvc.perform(get("/api/recommendations/courses/rc_none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECOMMENDATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("코스 상세: 만료된 ID 면 410 RECOMMENDATION_EXPIRED")
    void getCourseDetail_expired() throws Exception {
        given(recommendService.getCourseDetail(any()))
                .willThrow(new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED));

        mockMvc.perform(get("/api/recommendations/courses/rc_old"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("RECOMMENDATION_EXPIRED"));
    }

    // ===================== 장소 교체 후보 =====================

    @Test
    @DisplayName("교체 후보: 주소의 코스 ID·장소 ID 를 서비스에 넘기고 200 으로 후보 목록을 돌려준다")
    void getAlternatives_ok() throws Exception {
        AlternativePlaceListResponse response = new AlternativePlaceListResponse("rc_1",
                new CurrentPlaceResponse("101", 1, "카페 A", Category.CAFE, "카페", 60),
                List.of(new AlternativePlaceResponse("201", "카페 C", Category.CAFE, "카페", "서울",
                        37.545, 127.057, null, 60, 8, null, -2, false, true)));
        given(placeReplaceService.getAlternatives("rc_1", "101")).willReturn(response);

        mockMvc.perform(get("/api/recommendations/courses/rc_1/places/101/alternatives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPlace.placeId").value("101"))
                .andExpect(jsonPath("$.data.alternatives[0].placeId").value("201"))
                .andExpect(jsonPath("$.data.alternatives[0].diffMinutes").value(-2))
                .andExpect(jsonPath("$.data.alternatives[0].estimated").value(true));
    }

    @Test
    @DisplayName("교체 후보: 코스에 없는 장소면 404 RECOMMENDATION_NOT_FOUND")
    void getAlternatives_notFound() throws Exception {
        given(placeReplaceService.getAlternatives(any(), any()))
                .willThrow(new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND));

        mockMvc.perform(get("/api/recommendations/courses/rc_1/places/999/alternatives"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECOMMENDATION_NOT_FOUND"));
    }

    // ===================== 장소 교체 =====================

    @Test
    @DisplayName("교체: 주소의 코스 ID 와 요청 본문을 서비스에 넘기고 200 으로 새 버전 상세를 돌려준다")
    void replace_ok() throws Exception {
        given(placeReplaceService.replace(eq("rc_1"), eq(new PlaceReplaceRequest("101", "201"))))
                .willReturn(detailResponse());

        mockMvc.perform(post("/api/recommendations/courses/rc_1/replace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "targetPlaceId": "101", "newPlaceId": "201" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recommendationId").value("rc_1"));
    }

    @Test
    @DisplayName("교체: 새 장소 ID 가 비어 있으면 400 INVALID_REQUEST 이고 서비스는 부르지 않는다")
    void replace_badRequest_whenBlank() throws Exception {
        mockMvc.perform(post("/api/recommendations/courses/rc_1/replace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "targetPlaceId": "101", "newPlaceId": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(placeReplaceService);
    }

    @Test
    @DisplayName("교체: 이용 시간을 넘으면 409 AVAILABLE_TIME_EXCEEDED")
    void replace_conflict() throws Exception {
        given(placeReplaceService.replace(any(), any()))
                .willThrow(new CustomException(RecommendErrorCode.AVAILABLE_TIME_EXCEEDED));

        mockMvc.perform(post("/api/recommendations/courses/rc_1/replace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "targetPlaceId": "101", "newPlaceId": "201" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AVAILABLE_TIME_EXCEEDED"));
    }

    private RecommendCourseDetailResponse detailResponse() {
        return new RecommendCourseDetailResponse("rc_1", "rs_1",
                OffsetDateTime.of(2026, 11, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9)),
                new LocationResponse("성수역", null, 37.5446, 127.0559), null,
                TransportType.WALK, 180, "카페 A", 1, "13:00", 70, 10, 0, false,
                List.of(), List.of());
    }

    private String validBody() {
        return """
                {
                  "origin": { "name": "성수역", "address": null, "latitude": 37.5446, "longitude": 127.0559 },
                  "destination": null,
                  "startTime": "13:00",
                  "availableMinutes": 180,
                  "transportType": "WALK",
                  "categoryMode": "DIRECT",
                  "categories": ["CAFE"]
                }
                """;
    }

    private RecommendResponse emptyResponse() {
        RecommendConditionResponse condition = new RecommendConditionResponse(
                new LocationResponse("성수역", null, 37.5446, 127.0559), null,
                "13:00", 180, TransportType.WALK, CategoryMode.RANDOM, List.of());
        return new RecommendResponse("rs_1", OffsetDateTime.of(2026, 11, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9)),
                condition, List.of(), List.of());
    }
}
