package com.teumteum.recommend.controller;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.recommend.dto.AlternativePlaceListResponse;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.dto.RecommendRequest;
import com.teumteum.recommend.dto.RecommendResponse;
import com.teumteum.recommend.service.PlaceReplaceService;
import com.teumteum.recommend.service.RecommendService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PlaceReplaceService placeReplaceService;

    /** 추천 생성 (CON-01 '추천받기' → RES-01) */
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendResponse>> recommend(@Valid @RequestBody RecommendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.recommend(request)));
    }

    /**
     * 다시 추천 (RES-01 '다시 추천'). 요청 본문 없이 보관한 조건으로 다시 찾고, 새 recommendationSetId 로 응답한다.
     * 없으면 404, 만료면 410, 같은 사용자가 1분에 5번 넘게 누르면 429.
     * <p>
     * 비회원도 쓰므로 사용자는 IP 주소로 구분한다. HttpServletRequest 를 매개변수에 적으면 스프링이 지금 요청을 넣어 준다.
     * (배포 때 Nginx 같은 프록시를 두면 getRemoteAddr() 가 프록시 IP 가 되므로 server.forward-headers-strategy 설정 필요)
     */
    @PostMapping("/{recommendationSetId}/regenerate")
    public ResponseEntity<ApiResponse<RecommendResponse>> regenerate(@PathVariable String recommendationSetId,
                                                                     HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                recommendService.regenerate(recommendationSetId, request.getRemoteAddr())));
    }

    /** 추천 코스 상세 (RES-01 코스 카드 → DET-01 일정, DET-02 지도). 없으면 404, 만료면 410 */
    @GetMapping("/courses/{recommendationId}")
    public ResponseEntity<ApiResponse<RecommendCourseDetailResponse>> getCourseDetail(
            @PathVariable String recommendationId) {
        return ResponseEntity.ok(ApiResponse.ok(recommendService.getCourseDetail(recommendationId)));
    }

    /**
     * 장소 교체 후보 (DET-01 '장소 바꾸기' → DET-03). 같은 세분류 우선 5곳과 바꾸면 달라지는 총 시간.
     * 없는 코스·코스에 없는 장소면 404, 만료면 410, 후보가 없으면 alternatives []
     */
    @GetMapping("/courses/{recommendationId}/places/{placeId}/alternatives")
    public ResponseEntity<ApiResponse<AlternativePlaceListResponse>> getAlternatives(
            @PathVariable String recommendationId, @PathVariable String placeId) {
        return ResponseEntity.ok(ApiResponse.ok(placeReplaceService.getAlternatives(recommendationId, placeId)));
    }
}
