package com.teumteum.admin.controller;

import com.teumteum.admin.dto.AdminMemberResponse;
import com.teumteum.admin.dto.PageResponse;
import com.teumteum.admin.service.AdminMemberService;
import com.teumteum.global.auth.LoginMember;
import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * [이 파일이 하는 일]
 * 관리자 회원 관리 API (REQ-045). /api/admin/** 라서 관리자 토큰이 있어야 들어온다 (SecurityConfig, 아니면 403).
 */
@Tag(name = OpenApiConfig.TAG_ADMIN)
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @Operation(summary = "🔒 회원 목록", description = "관리자 전용. 최신 가입순. keyword 를 주면 이메일·닉네임에서 찾는다")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminMemberResponse>>> getMembers(
            @Parameter(description = "검색어 (이메일·닉네임, 선택)", example = "test") @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지 개수 (1~50)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminMemberService.getMembers(keyword, page, size)));
    }

    @Operation(summary = "🔒 회원 강제 탈퇴", description = "관리자 전용. 상태를 WITHDRAWN 으로 바꿔 로그인을 막는다.\n\n"
            + "본인·다른 관리자는 불가(400), 이미 탈퇴면 409, 없는 회원 404")
    @PatchMapping("/{memberId}/withdraw")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> withdraw(
            @LoginMember Long adminMemberId,
            @Parameter(description = "탈퇴시킬 회원 번호", example = "2") @PathVariable Long memberId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminMemberService.withdraw(adminMemberId, memberId)));
    }
}
