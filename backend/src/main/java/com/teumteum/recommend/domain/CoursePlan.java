package com.teumteum.recommend.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 완성된 추천 코스 1개 (이동시간 API 로 계산까지 끝난 상태).
 * (5) 추천 API 에서 이 모양을 RecommendCourseResponse 로 바꿔 응답하고 보관한다.
 *
 * @param stops              방문 순서대로 방문지 (1 ~ 4곳)
 * @param finalTravel        마지막 장소 → 도착지 이동 결과. 도착지가 없으면 null
 * @param totalMinutes       총 소요시간(분) = 체류 합 + 이동 합 (REQ-023)
 * @param totalTravelMinutes 이동시간 합(분), 마지막 → 도착지 포함
 * @param estimated          직선거리로 추정한 구간이 하나라도 있으면 true (NFR-025)
 */
public record CoursePlan(
        List<CourseStop> stops,
        TravelRoute finalTravel,
        int totalMinutes,
        int totalTravelMinutes,
        boolean estimated
) {

    public CoursePlan {
        stops = List.copyOf(stops);
    }

    /** 마지막 장소 → 도착지 이동시간. 도착지가 없으면 0 (명세 시간 계산 규칙) */
    public int finalTravelMinutes() {
        return finalTravel == null ? 0 : finalTravel.travelMinutes();
    }

    /** 코스에 들어간 장소 ID 묶음. 같은 묶음이면 같은 코스로 본다 (서로 다른 코스 3개 고를 때 사용) */
    public Set<String> placeIds() {
        return stops.stream()
                .map(stop -> stop.place().placeId())
                .collect(Collectors.toSet());
    }
}
