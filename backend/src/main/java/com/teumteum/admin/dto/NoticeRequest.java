package com.teumteum.admin.dto;

import com.teumteum.admin.domain.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지사항 등록·수정 요청 (REQ-049). 틀리면 400 INVALID_REQUEST + fieldErrors */
@Schema(description = "공지사항 등록·수정 요청")
public record NoticeRequest(
        @Schema(description = "제목 (최대 200자)", example = "틈틈 서비스 오픈 안내")
        @NotBlank
        @Size(max = Notice.TITLE_MAX_LENGTH)
        String title,

        @Schema(description = "본문", example = "남는 시간, 틈틈이 코스를 찾아 드려요!")
        @NotBlank
        @Size(max = 10_000)
        String content
) {
}
