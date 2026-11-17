package com.teumteum.course.dto;

// 저장 코스 목록에서 카드 한 건에 필요한 요약 정보를 전달한다.
// docs/course-api.md의 CourseSummaryResponse 정의를 따른다.
// 이후 CourseListResponse의 content에 이 DTO 여러 개를 목록으로 담는다.
// record는 생성자와 courseId() 같은 접근자, equals/hashCode/toString을 자동으로 제공한다.
// DB 조회나 값 변환을 직접 수행하지 않으며, 조회한 값을 생성자에 전달해 만든다.
public record CourseSummaryResponse(
        // 저장 코스 ID. 엔티티의 Long을 문자열로 변환하며, 상세 조회에 사용한다.
        String courseId,
        // 저장에 사용한 원본 추천의 불변 버전 ID. 저장 코스 ID와는 다른 값이다.
        String sourceRecommendationId,
        // 저장 당시 코스명. 예: "틈카페 → 산책공원".
        String courseName,
        // 이 코스에 저장된 장소 수(1~4개). 조회 시 집계한 값을 전달한다.
        Integer placeCount,
        // 저장 당시 이동시간과 체류시간을 합한 총 소요시간(분).
        Integer totalMinutes,
        // 저장 시각 문자열. 명세 예: "2026-11-12T10:00:00+09:00".
        // LocalDateTime에는 시간대가 없으므로 저장 기준 시간대를 확인한 뒤 변환한다.
        String createdAt
) {
}
