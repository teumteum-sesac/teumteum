package com.teumteum.recommend.controller;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.exception.GlobalExceptionHandler;
import com.teumteum.place.exception.PlaceErrorCode;
import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.dto.LocationResponse;
import com.teumteum.recommend.dto.RecommendConditionResponse;
import com.teumteum.recommend.dto.RecommendResponse;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @BeforeEach
    void setUp() {
        recommendService = mock(RecommendService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendController(recommendService))
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
