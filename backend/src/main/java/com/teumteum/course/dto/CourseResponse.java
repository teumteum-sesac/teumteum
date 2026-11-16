package com.teumteum.course.dto;

import java.util.List;

// 저장 코스 상세 전체를 전달하는 응답 DTO다. 목록 응답 DTO와는 별개다.
// Service에서 본인 소유 코스인지 확인한 뒤 코스·장소·경로 조회 결과를 조합해 만든다.
// 하위 DTO의 값을 자동으로 조회하거나 채우지는 않는다. 각 생성자 인수는 변환 코드에서 전달한다.
// 기본 상세는 저장 당시 정보를 사용하며, 최신 장소 정보로 저장본을 덮어쓰지 않는다.
public record CourseResponse(
        // 저장 코스의 PK. API 명세에 따라 엔티티의 Long을 문자열로 변환한다.
        String courseId,
        // 저장한 원본 추천 버전 ID. 원래부터 문자열인 값이다.
        String sourceRecommendationId,
        String courseName,
        // 저장 시각. 명세 예: "2026-11-12T10:00:00+09:00".
        // 엔티티의 LocalDateTime에는 시간대가 없으므로 저장 시각의 기준 시간대를 확인해 변환해야 한다.
        String createdAt,
        // 출발지 정보는 필수다. 이름·주소는 없어도 좌표는 있어야 한다.
        CourseLocationResponse origin,
        // 도착지 미설정 시 이 필드 자체를 null로 전달한다.
        CourseLocationResponse destination,
        // 이동수단 enum의 이름: "TRANSIT", "WALK", "CAR".
        String transportType,
        // 출발 시각을 "HH:mm" 문자열로 변환한다. 예: "13:00".
        String startTime,
        // 체류와 이동을 모두 포함한 총 소요시간(분).
        Integer totalMinutes,
        // 출발지→장소들→선택 도착지까지의 이동시간 합계(분).
        Integer totalTravelMinutes,
        // 마지막 장소→도착지 이동시간(분). 도착지가 없으면 0이다.
        Integer finalTravelMinutes,
        // 이동시간 추정 구간 포함 여부. 경로가 직선인지 나타내는 pathType과는 별개다.
        boolean estimated,
        // 저장한 장소 1~4개를 방문 순서대로 담는다.
        List<CoursePlaceResponse> places,
        // 경로 구간들을 출발 순서대로 담는다. 각 구간 안에는 좌표 DTO 목록이 들어 있다.
        List<CourseRouteLegResponse> routeLegs
) {
}
