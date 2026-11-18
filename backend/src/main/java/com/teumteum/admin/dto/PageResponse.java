package com.teumteum.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 페이지 단위 목록 응답 (회원 목록·공지 목록 공통). 저장 코스 목록(CourseListResponse)과 같은 필드 이름을 쓴다.
 * Spring 의 Page 를 그대로 내보내면 내부 필드가 많고 모양이 버전마다 달라서, 필요한 값만 담는다.
 */
@Schema(description = "페이지 목록")
public record PageResponse<T>(
        @Schema(description = "이번 페이지 항목") List<T> content,
        @Schema(description = "페이지 번호 (0부터)", example = "0") int page,
        @Schema(description = "한 페이지 개수", example = "20") int size,
        @Schema(description = "전체 개수", example = "35") long totalElements,
        @Schema(description = "전체 페이지 수", example = "2") int totalPages,
        @Schema(description = "다음 페이지가 있는지") boolean hasNext
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
