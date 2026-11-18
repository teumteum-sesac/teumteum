package com.teumteum.course.dto;

// 코스 저장 요청을 처리한 뒤 프론트에 전달하는 결과다.
public record CourseSaveResponse(
        // DB에 저장한 코스 ID. 추천 ID와 다르며, 저장 코스 상세 조회·삭제에 사용한다.
        String courseId,
        // 저장 당시의 코스 이름. 예: "틈카페 → 서울숲".
        String courseName,
        // 저장 날짜·시각. 시간대가 포함된 ISO 8601 문자열로 반환한다.
        String createdAt,
        // 새로 저장했으면 true, 이미 저장돼 있던 코스를 반환하면 false다.
        boolean created
) {
}
