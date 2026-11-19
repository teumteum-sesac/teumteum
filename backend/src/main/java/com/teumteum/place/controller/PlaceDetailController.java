package com.teumteum.place.controller;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.config.OpenApiConfig;
import com.teumteum.place.dto.PlaceDetailResponse;
import com.teumteum.place.service.PlaceDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 상세 API (REQ-017, DET-01). 주변 탐색·홈 검색·추천 결과 상세·저장한 코스에서 장소를 누르면 호출한다.
 */
@Tag(name = OpenApiConfig.TAG_PLACE)
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceDetailController {

    private final PlaceDetailService placeDetailService;

    /**
     * 예: GET /api/places/detail?kakaoPlaceId=10926416&name=할머니냉면&lat=37.5822701&lng=127.0437006
     * 이름·좌표는 목록에서 받은 값을 그대로 넘긴다 (카카오 로컬에는 ID 로 한 곳을 조회하는 기능이 없음).
     */
    @Operation(summary = "장소 상세 (카카오 장소 ID)", description = "카카오 장소의 이름·좌표로 TourAPI 를 찾아 사진·소개·운영시간·쉬는 날을 돌려준다. TourAPI 에 없으면 카카오맵 링크만 온다.\n\n쓰는 화면: 홈 검색·주변 탐색의 장소 미리보기, 추천 코스의 장소 (추천 코스의 placeId 는 카카오 장소 ID)", security = {})
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<PlaceDetailResponse>> getDetail(
            @Parameter(description = "카카오 장소 ID", example = "10926416") @RequestParam @NotBlank String kakaoPlaceId,
            @Parameter(description = "장소명", example = "할머니냉면") @RequestParam @NotBlank String name,
            @Parameter(description = "위도", example = "37.5822701") @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @Parameter(description = "경도", example = "127.0437006") @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng
    ) {
        return ResponseEntity.ok(ApiResponse.ok(placeDetailService.getDetail(kakaoPlaceId, name, lat, lng)));
    }

    /**
     * 예: GET /api/places/12/detail
     * 저장한 코스의 장소처럼 우리 장소 ID(place 테이블)만 아는 경우에 쓴다. 응답은 위 /detail 과 같다.
     */
    @Operation(summary = "장소 상세 (우리 장소 ID)", description = "place 테이블의 장소 ID 로 상세를 조회한다. 응답은 카카오 장소 ID 로 조회할 때와 같다. 없는 ID 면 404 PLACE_NOT_FOUND.\n\n쓰는 화면: 저장한 코스 상세·지도 (저장 코스의 placeId 는 우리 장소 ID)", security = {})
    @GetMapping("/{placeId}/detail")
    public ResponseEntity<ApiResponse<PlaceDetailResponse>> getDetailByPlaceId(
            @Parameter(description = "우리 장소 ID (place 테이블)", example = "1") @PathVariable Long placeId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(placeDetailService.getDetailByPlaceId(placeId)));
    }
}
