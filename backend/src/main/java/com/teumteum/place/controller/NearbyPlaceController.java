package com.teumteum.place.controller;

import com.teumteum.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.teumteum.global.common.ApiResponse;
import com.teumteum.place.domain.PlaceCategory;
import com.teumteum.place.dto.NearbyPlaceResult;
import com.teumteum.place.service.NearbyPlaceService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주변 장소 후보 검색 API. 추천 서비스는 NearbyPlaceService 를 직접 호출하고, 이 API 는 Swagger·프론트 확인용이다.
 */
@Tag(name = OpenApiConfig.TAG_PLACE)
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class NearbyPlaceController {

    private final NearbyPlaceService nearbyPlaceService;

    /**
     * 예: GET /api/places/nearby?lat=37.5445&lng=127.0557&categories=CAFE,FOOD&radius=1000
     * categories 를 비우면 대분류 구분 없이(랜덤 추천) 조회한다.
     */
    @Operation(summary = "주변 장소", description = "좌표 주변의 장소 후보를 찾는다. 대분류(categories)를 비우면 전체.\n\n쓰는 화면: 주변 장소 탐색(HOM-02). 추천도 같은 로직으로 후보를 모은다", security = {})
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyPlaceResult>>> searchNearby(
            @Parameter(description = "위도", example = "37.5446") @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @Parameter(description = "경도", example = "127.0559") @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @Parameter(description = "대분류 여러 개 (CULTURE, CAFE, FOOD, NATURE, ENTERTAINMENT, SHOPPING, SPORTS, TOURISM). 비우면 전체", example = "CAFE,FOOD") @RequestParam(required = false) Set<PlaceCategory> categories,
            @Parameter(description = "반경(m), 기본 1000, 최대 20000", example = "1000") @RequestParam(defaultValue = "1000") @Min(1) @Max(NearbyPlaceService.MAX_RADIUS) int radius
    ) {
        return ResponseEntity.ok(ApiResponse.ok(nearbyPlaceService.findCandidates(lat, lng, categories, radius)));
    }
}