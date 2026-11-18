package com.teumteum.recommend.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 보관하는 추천 묶음 1개 (한 번의 추천 요청 결과, recommendationSetId 로 찾는 단위).
 *
 * @param recommendationSetId 묶음 ID ("rs_" + UUID)
 * @param condition           실제 적용한 조건. 다시 추천은 이 조건으로 다시 계산한다
 * @param courses             이 묶음의 코스 0 ~ 3개 (정렬 없음)
 * @param createdAt           만든 시각
 * @param expiresAt           만료 시각 = 만든 시각 + 2시간 (REQ-056)
 * @param candidates          코스를 만들 때 본 장소 후보. 장소 교체 후보를 여기서 고른다 (명세 4장)
 */
public record RecommendationSet(
        String recommendationSetId,
        RecommendCondition condition,
        List<RecommendedCourse> courses,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        List<PlaceCandidate> candidates
) {

    public RecommendationSet {
        courses = List.copyOf(courses);
        candidates = (candidates == null) ? List.of() : List.copyOf(candidates);
    }

    /**
     * 후보 없이 만드는 생성자 (교체 후보가 필요 없는 테스트 등).
     * 이 생성자 덕분에 기존 new RecommendationSet(ID, 조건, 코스, 만든 시각, 만료 시각) 코드를 고치지 않아도 된다.
     */
    public RecommendationSet(String recommendationSetId, RecommendCondition condition,
                             List<RecommendedCourse> courses, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this(recommendationSetId, condition, courses, createdAt, expiresAt, List.of());
    }

    /** 이 시각에 만료됐는지. 만료 시각과 같거나 지났으면 만료 */
    public boolean isExpiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
