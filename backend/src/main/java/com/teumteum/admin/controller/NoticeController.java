package com.teumteum.admin.controller;

import com.teumteum.admin.dto.NoticeResponse;
import com.teumteum.admin.dto.PageResponse;
import com.teumteum.admin.service.NoticeService;
import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * [이 파일이 하는 일]
 * 누구나 보는 공지사항 조회 API (REQ-049). 로그인 없이 쓴다.
 * 홈 상단 배너(전체 알림, REQ-053)는 GET /api/notices?size=1 로 가장 최근 공지 1건을 가져다 쓴다.
 */
@Tag(name = OpenApiConfig.TAG_NOTICE)
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "공지 목록", description = "최신 등록순. 홈 배너는 size=1 로 가장 최근 공지만 가져온다", security = {})
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NoticeResponse>>> getNotices(
            @Parameter(description = "페이지 번호 (0부터)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지 개수 (1~50)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(noticeService.getNotices(page, size)));
    }

    @Operation(summary = "공지 상세", description = "없으면 404 NOTICE_NOT_FOUND", security = {})
    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeResponse>> getNotice(
            @Parameter(description = "공지 번호", example = "1") @PathVariable Long noticeId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(noticeService.getNotice(noticeId)));
    }
}
