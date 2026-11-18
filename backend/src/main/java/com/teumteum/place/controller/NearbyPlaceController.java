package com.teumteum.place.controller;

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
 * 주변 장소 검색 API.
 * 주변 탐색 화면(HOM-02, REQ-051)이 사용자가 고른 반경(500m / 1km / 3km)과 대분류로 호출한다.
 * 추천 서비스(REQ-015·016)는 이 API 대신 NearbyPlaceService 를 직접 호출한다.
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class NearbyPlaceController {

    private final NearbyPlaceService nearbyPlaceService;

    /**
     * 예: GET /api/places/nearby?lat=37.5445&lng=127.0557&categories=CAFE,FOOD&radius=1000
     * categories 를 비우면 대분류 구분 없이(랜덤 추천) 조회한다.
     */
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyPlaceResult>>> searchNearby(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam(required = false) Set<PlaceCategory> categories,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(NearbyPlaceService.MAX_RADIUS) int radius
    ) {
        return ResponseEntity.ok(ApiResponse.ok(nearbyPlaceService.findCandidates(lat, lng, categories, radius)));
    }
}