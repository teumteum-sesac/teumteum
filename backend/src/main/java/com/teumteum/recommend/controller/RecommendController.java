package com.teumteum.recommend.controller;

import com.teumteum.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.teumteum.global.common.ApiResponse;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.dto.RecommendRequest;
import com.teumteum.recommend.dto.RecommendResponse;
import com.teumteum.recommend.service.RecommendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 추천 API (recommend-api.md). 로그인 없이 쓴다 (SecurityConfig 에서 /api/courses 등만 인증 필요).
 * <p>
 * 컨트롤러는 "요청 받기 → 서비스 부르기 → ApiResponse 로 감싸기" 만 한다.
 * - 요청 검증: @Valid 가 RecommendRequest 의 @NotNull·@Min 등을 검사하고, 틀리면 400 INVALID_REQUEST (GlobalExceptionHandler)
 * - 오류: 서비스가 던진 CustomException 을 GlobalExceptionHandler 가 실패 응답으로 바꾼다 → 여기서 try-catch 하지 않는다
 */
@Tag(name = OpenApiConfig.TAG_RECOMMEND)
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /** 추천 생성 (CON-01 '추천받기' → RES-01) */
    @Operation(summary = "추천 생성", description = "외출 조건(출발지·시작 시각·이용 시간·이동수단·카테고리)으로 코스를 최대 3개 추천한다. 이용 시간이 짧으면 1곳, 길면 최대 4곳.\n\n- 맞는 코스가 없으면 오류가 아니라 courses: [] (200)\n- 결과는 2시간 보관 (expiresAt). recommendationSetId 로 다시 추천, courses[].recommendationId 로 코스 상세\n- 쓰는 화면: 외출 조건 [추천받기] → 추천 결과", security = {})
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendResponse>> recommend(@Valid @RequestBody RecommendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.recommend(request)));
    }

    /**
     * 다시 추천 (RES-01 '다시 추천'). 요청 본문 없이 보관한 조건으로 다시 찾고, 새 recommendationSetId 로 응답한다.
     * 없으면 404, 만료면 410. (너무 자주 누르면 429 는 5-3 에서)
     */
    @Operation(summary = "다시 추천", description = "같은 조건으로 다른 조합을 다시 추천한다. 요청 본문 없음, 새 recommendationSetId 로 응답.\n\n없으면 404, 2시간 지나 만료면 410", security = {})
    @PostMapping("/{recommendationSetId}/regenerate")
    public ResponseEntity<ApiResponse<RecommendResponse>> regenerate(
            @Parameter(description = "추천 생성 응답의 recommendationSetId") @PathVariable String recommendationSetId) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.regenerate(recommendationSetId)));
    }

    /** 추천 코스 상세 (RES-01 코스 카드 → DET-01 일정, DET-02 지도). 없으면 404, 만료면 410 */
    @Operation(summary = "추천 코스 상세", description = "추천 코스 1개의 일정(방문 시각·체류·이동)과 지도 경로(routeLegs). 쓰는 화면: 코스 상세, 코스 지도.\n\n없으면 404, 만료면 410", security = {})
    @GetMapping("/courses/{recommendationId}")
    public ResponseEntity<ApiResponse<RecommendCourseDetailResponse>> getCourseDetail(
            @Parameter(description = "추천 생성 응답 courses[].recommendationId") @PathVariable String recommendationId) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.getCourseDetail(recommendationId)));
    }
}
