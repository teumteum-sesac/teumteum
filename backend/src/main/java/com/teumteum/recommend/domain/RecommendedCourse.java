package com.teumteum.recommend.domain;

import java.time.OffsetDateTime;

/**
 * 보관하는 추천 코스 1개 (recommendationId 로 찾는 단위).
 * <p>
 * 상세·지도·장소 교체·코스 저장이 모두 이 ID 로 들어온다.
 * 장소를 교체하면 원래 코스는 그대로 두고 새 ID 로 새 RecommendedCourse 를 만든다 (불변 버전, 명세 5장).
 *
 * @param recommendationId    코스 ID ("rc_" + UUID)
 * @param recommendationSetId 이 코스가 속한 묶음 ID. 조건을 찾을 때 쓴다
 * @param plan                코스 내용 (방문지·이동·총 시간·지도 좌표)
 * @param expiresAt           만료 시각 (묶음과 같음)
 */
public record RecommendedCourse(
        String recommendationId,
        String recommendationSetId,
        CoursePlan plan,
        OffsetDateTime expiresAt
) {

    /** 이 시각에 만료됐는지. 만료 시각과 같거나 지났으면 만료 */
    public boolean isExpiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
