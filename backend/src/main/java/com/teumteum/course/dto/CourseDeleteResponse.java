package com.teumteum.course.dto;

// 저장 코스 삭제 성공 결과를 전달하는 응답 DTO다.
// docs/course-api.md의 삭제 응답 정의를 따르며, 실제 응답은 ApiResponse로 감싼다.
// 삭제 작업은 Service가 수행한다. 이 DTO를 만드는 것만으로 DB 데이터가 삭제되지는 않는다.
// record가 생성자와 courseId(), deleted() 접근자 등을 자동으로 제공한다.
public record CourseDeleteResponse(
        // 삭제한 저장 코스의 ID. 엔티티의 Long을 문자열로 변환해 전달한다.
        String courseId,

        // 삭제 성공 시 true다. 실패하면 이 DTO 대신 공통 오류 응답을 반환한다.
        // 없거나 다른 회원의 코스, 이미 삭제된 코스는 명세상 404로 처리한다.
        boolean deleted
) {
}
