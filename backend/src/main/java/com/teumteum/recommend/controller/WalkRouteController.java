package com.teumteum.recommend.controller;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.config.OpenApiConfig;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.dto.CoordinateResponse;
import com.teumteum.recommend.dto.WalkRouteResponse;
import com.teumteum.recommend.service.TravelTimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 두 지점 사이 도보 경로 API. 장소 상세(/places/:placeId) 지도에 내 위치 → 가게 길을 그릴 때 쓴다.
 * 새 계산 로직은 없다: 추천이 쓰는 TravelTimeService.findRoute(WALK) 를 그대로 불러 화면에서 쓸 수 있게 이어 줄 뿐이다
 * (OpenRouteService 도보, 실패하면 직선 추정). 로그인 없이 쓸 수 있다.
 */
@Tag(name = OpenApiConfig.TAG_RECOMMEND)
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class WalkRouteController {

    private final TravelTimeService travelTimeService;

    /** 예: GET /api/routes/walk?fromLat=37.5666&fromLng=126.9784&toLat=37.5700&toLng=126.9820 */
    @Operation(summary = "도보 경로", description = "출발 좌표에서 도착 좌표까지 도보 경로와 시간. 경로 API 실패 시 직선 추정(estimated true)")
    @GetMapping("/walk")
    public ResponseEntity<ApiResponse<WalkRouteResponse>> walk(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double fromLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng
    ) {
        TravelRoute route = travelTimeService.findRoute(TransportType.WALK, fromLat, fromLng, toLat, toLng);
        // 경로 좌표가 없으면(직선 추정) 출발·도착 두 점으로 직선을 그린다
        List<CoordinateResponse> path = route.coordinates().isEmpty()
                ? List.of(new CoordinateResponse(fromLat, fromLng), new CoordinateResponse(toLat, toLng))
                : route.coordinates().stream().map(c -> new CoordinateResponse(c.latitude(), c.longitude())).toList();
        return ResponseEntity.ok(ApiResponse.ok(new WalkRouteResponse(route.travelMinutes(), route.estimated(), path)));
    }
}
