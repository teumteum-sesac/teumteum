package com.teumteum.recommend.domain;

import java.util.List;

/**
 * 코스 찾기 결과 (CourseSearchService.findCourses 의 반환 모양).
 * <p>
 * 코스만이 아니라 "코스를 만들 때 본 장소 후보" 도 같이 돌려준다.
 * 추천 묶음과 함께 보관해 두었다가 장소 교체 후보(DET-03)를 고를 때 쓴다 (명세 4장 "추천할 때 받아 둔 후보를 쓴다").
 * → 교체 후보 화면을 열 때 장소 API 를 다시 부르지 않는다.
 *
 * @param plans      찾은 코스 0 ~ 3개
 * @param candidates 반경을 넓히며 받은 장소 후보 전체 (장소 ID 로 중복 제거, 처음 받은 순서)
 */
public record CourseSearchResult(
        List<CoursePlan> plans,
        List<PlaceCandidate> candidates
) {

    public CourseSearchResult {
        plans = List.copyOf(plans);
        candidates = List.copyOf(candidates);
    }
}
