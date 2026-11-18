package com.teumteum.admin.dto;

import com.teumteum.admin.domain.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 공지사항 1건 (목록·상세 공통) */
@Schema(description = "공지사항")
public record NoticeResponse(
        @Schema(description = "공지 번호", example = "1") Long noticeId,
        @Schema(description = "제목") String title,
        @Schema(description = "본문") String content,
        @Schema(description = "등록 시각") LocalDateTime createdAt,
        @Schema(description = "마지막 수정 시각") LocalDateTime updatedAt
) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(),
                notice.getCreatedAt(), notice.getUpdatedAt());
    }
}
