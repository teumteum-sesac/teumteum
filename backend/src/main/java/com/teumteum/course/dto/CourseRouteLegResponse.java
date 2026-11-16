package com.teumteum.course.dto;

import java.util.List;

// 저장한 코스의 경로 한 구간을 프론트에 전달하는 응답 DTO다. 예: 출발지 → 첫 번째 카페.
// 전체 경로는 이 DTO 여러 개를 List<CourseRouteLegResponse>로 묶어 표현한다.
// record는 생성자, 각 값의 접근자, equals/hashCode/toString을 자동으로 제공한다.
public record CourseRouteLegResponse(
        // 출발 지점 순서. 출발지는 0, 방문 장소는 1~N이다.
        Integer fromVisitOrder,
        // 도착 지점 순서. 다음 장소 또는 최종 도착지를 가리킨다. 저장된 값은 출발 순서 + 1이다.
        Integer toVisitOrder,
        // 경로 종류: "ACTUAL"은 실제 경로, "STRAIGHT"는 직선 경로다.
        // 경로를 계산하거나 좌표 개수를 자동으로 변경하는 값이 아니라, 저장된 경로의 종류를 전달한다.
        String pathType,
        // 이 구간의 좌표들을 진행 방향 순서대로 담는다. 좌표 개수는 고정되지 않으며 최소 2개다.
        // 직선은 보통 시작·끝 두 좌표, 실제 경로는 API에서 받은 중간 좌표들도 포함한다.
        // Service에서 엔티티의 저장된 좌표들을 CourseCoordinateResponse로 변환해 전달한다.
        List<CourseCoordinateResponse> coordinates
) {
}
