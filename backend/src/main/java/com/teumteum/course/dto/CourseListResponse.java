package com.teumteum.course.dto;

import java.util.List;

// 저장 코스 목록과 페이지 정보를 함께 전달하는 응답 DTO다.
public record CourseListResponse(
        // 현재 페이지의 코스 목록. 각 항목은 카드 한 건에 해당한다.
        List<CourseSummaryResponse> content,

        // 현재 페이지 번호. 첫 페이지는 0이다.
        Integer page,

        // 한 페이지에 요청한 코스 개수.
        Integer size,

        // 본인이 저장한 코스의 전체 개수.
        Long totalElements,

        // 전체 페이지 수.
        Integer totalPages,

        // 다음 페이지가 있으면 true.
        boolean hasNext
) {
}