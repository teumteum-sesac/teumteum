package com.teumteum.recommend.controller;

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
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /** 추천 생성 (CON-01 '추천받기' → RES-01) */
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendResponse>> recommend(@Valid @RequestBody RecommendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.recommend(request)));
    }

    /**
     * 다시 추천 (RES-01 '다시 추천'). 요청 본문 없이 보관한 조건으로 다시 찾고, 새 recommendationSetId 로 응답한다.
     * 없으면 404, 만료면 410. (너무 자주 누르면 429 는 5-3 에서)
     */
    @PostMapping("/{recommendationSetId}/regenerate")
    public ResponseEntity<ApiResponse<RecommendResponse>> regenerate(@PathVariable String recommendationSetId) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.regenerate(recommendationSetId)));
    }

    /** 추천 코스 상세 (RES-01 코스 카드 → DET-01 일정, DET-02 지도). 없으면 404, 만료면 410 */
    @GetMapping("/courses/{recommendationId}")
    public ResponseEntity<ApiResponse<RecommendCourseDetailResponse>> getCourseDetail(
            @PathVariable String recommendationId) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.getCourseDetail(recommendationId)));
    }
}
