package com.teumteum.course.controller;

import com.teumteum.course.dto.CourseLatestPlacesResponse;
import com.teumteum.course.service.CourseLatestService;
import com.teumteum.global.auth.LoginMember;
import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@Tag(name = OpenApiConfig.TAG_COURSE)
@RequiredArgsConstructor
public class CourseLatestController {
    private final CourseLatestService service;

    @Operation(summary = "🔒 저장 코스 최신 장소 정보", description = "정보 새로고침 버튼으로만 호출한다. 저장본·일정·지도는 변경하지 않으며 SUCCESS·NOT_FOUND·FAILED를 장소별로 반환한다. 외부 조회 예산은 전체 7초다.")
    @GetMapping("/{courseId}/places/latest")
    public ResponseEntity<ApiResponse<CourseLatestPlacesResponse>> getLatest(
            @Parameter(hidden = true) @LoginMember Long memberId,
            @PathVariable("courseId") Long courseId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.getLatest(memberId, courseId)));
    }
}
