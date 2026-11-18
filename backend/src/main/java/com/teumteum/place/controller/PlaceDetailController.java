package com.teumteum.place.controller;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.place.dto.PlaceDetailResponse;
import com.teumteum.place.service.PlaceDetailService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 상세 API (REQ-017, DET-01). 주변 탐색·홈 검색·추천 결과 상세에서 장소를 누르면 호출한다.
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceDetailController {

    private final PlaceDetailService placeDetailService;

    /**
     * 예: GET /api/places/detail?kakaoPlaceId=10926416&name=할머니냉면&lat=37.5822701&lng=127.0437006
     * 이름·좌표는 목록에서 받은 값을 그대로 넘긴다 (카카오 로컬에는 ID 로 한 곳을 조회하는 기능이 없음).
     */
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<PlaceDetailResponse>> getDetail(
            @RequestParam @NotBlank String kakaoPlaceId,
            @RequestParam @NotBlank String name,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng
    ) {
        return ResponseEntity.ok(ApiResponse.ok(placeDetailService.getDetail(kakaoPlaceId, name, lat, lng)));
    }
}
