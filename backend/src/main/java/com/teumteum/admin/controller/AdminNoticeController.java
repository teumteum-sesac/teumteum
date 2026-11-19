package com.teumteum.admin.controller;

import com.teumteum.admin.dto.NoticeRequest;
import com.teumteum.admin.dto.NoticeResponse;
import com.teumteum.admin.service.NoticeService;
import com.teumteum.global.auth.LoginMember;
import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * [이 파일이 하는 일]
 * 관리자 공지사항 등록·수정·삭제 API (REQ-049). 관리자 토큰 필요 (SecurityConfig).
 * 조회는 누구나 쓰는 NoticeController(/api/notices) 에 있다.
 */
@Tag(name = OpenApiConfig.TAG_ADMIN)
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "🔒 공지 등록", description = "관리자 전용. 가장 최근 공지는 홈 상단 배너(전체 알림)로도 보인다")
    @PostMapping
    public ResponseEntity<ApiResponse<NoticeResponse>> create(
            @LoginMember Long adminMemberId,
            @Valid @RequestBody NoticeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(noticeService.create(adminMemberId, request)));
    }

    @Operation(summary = "🔒 공지 수정", description = "관리자 전용. 없으면 404 NOTICE_NOT_FOUND")
    @PatchMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeResponse>> update(
            @Parameter(description = "공지 번호", example = "1") @PathVariable Long noticeId,
            @Valid @RequestBody NoticeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(noticeService.update(noticeId, request)));
    }

    @Operation(summary = "🔒 공지 삭제", description = "관리자 전용. 없으면 404 NOTICE_NOT_FOUND")
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "공지 번호", example = "1") @PathVariable Long noticeId
    ) {
        noticeService.delete(noticeId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
