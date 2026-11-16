package com.teumteum.place.controller;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.place.dto.AddressResponse;
import com.teumteum.place.dto.PlaceSearchResult;
import com.teumteum.place.service.PlaceSearchService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소·주소 검색 API. 비회원도 사용할 수 있다 (NFR-002).
 * 파라미터 검증 실패 시 HandlerMethodValidationException 이 난다.
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    /**
     * 장소·주소 검색 (REQ-007 위치 설정 자동완성, REQ-052 홈 검색).
     * lat·lng 를 주면 그 좌표 기준 거리(distance)가 함께 온다.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PlaceSearchResult>>> search(
            @RequestParam @NotBlank @Size(max = 100) String query,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
    ) {
        return ResponseEntity.ok(ApiResponse.ok(placeSearchService.search(query, lat, lng)));
    }

    /**
     * 좌표→주소 변환 (현재 위치, 지도 핀 위치를 주소로 표시할 때).
     */
    @GetMapping("/address")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
    ) {
        return ResponseEntity.ok(ApiResponse.ok(placeSearchService.getAddress(lat, lng)));
    }
}
