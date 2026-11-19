package com.teumteum.recommend.domain;

/**
 * 코스 안의 방문지 1곳. 방문 순서는 CoursePlan.stops 안에서의 위치(0부터)로 정해진다.
 *
 * @param place       방문할 장소
 * @param stayMinutes 예상 체류시간(분). 세분류별 최소~최대 안에서 이 코스에 정한 값 (StayTimeCalculator·StayTimeFiller)
 * @param travel      직전 지점(첫 장소면 출발지) → 이 장소 이동 결과. 이동시간·노선·추정 여부가 들어 있다
 */
public record CourseStop(
        PlaceCandidate place,
        int stayMinutes,
        TravelRoute travel
) {
}
