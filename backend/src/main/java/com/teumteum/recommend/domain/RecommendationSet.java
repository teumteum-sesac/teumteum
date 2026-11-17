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
 */
public record RecommendationSet(
        String recommendationSetId,
        RecommendCondition condition,
        List<RecommendedCourse> courses,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {

    public RecommendationSet {
        courses = List.copyOf(courses);
    }

    /** 이 시각에 만료됐는지. 만료 시각과 같거나 지났으면 만료 */
    public boolean isExpiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
