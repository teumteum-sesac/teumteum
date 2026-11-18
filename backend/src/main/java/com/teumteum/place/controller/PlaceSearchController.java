package com.teumteum.place.controller;

import com.teumteum.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiConfig.TAG_PLACE)
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    /**
     * 장소·주소 검색 (REQ-007 위치 설정 자동완성, REQ-052 홈 검색).
     * lat·lng 를 주면 그 좌표 기준 거리(distance)가 함께 온다.
     */
    @Operation(summary = "장소·주소 검색", description = "검색어로 장소(PLACE)와 주소(ADDRESS)를 함께 찾는다. lat·lng 를 주면 결과마다 그 좌표에서의 거리(m)가 온다.\n\n쓰는 화면: 홈 검색창, 출발지·도착지 설정", security = {})
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PlaceSearchResult>>> search(
            @Parameter(description = "검색어 (1~100자)", example = "성수역") @RequestParam @NotBlank @Size(max = 100) String query,
            @Parameter(description = "기준 위도 (선택, 주면 거리 계산)", example = "37.5446") @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @Parameter(description = "기준 경도 (선택)", example = "127.0559") @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
    ) {
        return ResponseEntity.ok(ApiResponse.ok(placeSearchService.search(query, lat, lng)));
    }

    /**
     * 좌표→주소 변환 (현재 위치, 지도 핀 위치를 주소로 표시할 때).
     */
    @Operation(summary = "좌표 → 주소", description = "위도·경도를 주소로 바꾼다 (현재 위치·지도 중앙 핀 표시용). 못 찾으면 값이 모두 null", security = {})
    @GetMapping("/address")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @Parameter(description = "위도", example = "37.5446") @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @Parameter(description = "경도", example = "127.0559") @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
    ) {
        return ResponseEntity.ok(ApiResponse.ok(placeSearchService.getAddress(lat, lng)));
    }
}
