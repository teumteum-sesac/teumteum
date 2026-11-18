package com.teumteum.recommend.repository;

import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;

import java.util.Optional;

/**
 * 추천 결과 보관소 (REQ-056, 명세 "보관과 만료").
 * <p>
 * 인터페이스로 만든 이유: 지금은 서버 메모리에 보관하지만(InMemoryRecommendationStore),
 * 나중에 DB·캐시로 바꿔야 하면 이 인터페이스를 구현한 새 클래스만 만들면 된다.
 * 추천 서비스는 "어디에 보관하는지" 모르고 이 인터페이스만 쓴다.
 * <p>
 * 만료 판단은 서비스가 한다. 보관소는 만료된 결과도 정리 전까지는 그대로 돌려준다
 * → 서비스가 410(만료)과 404(없음)를 구분할 수 있다.
 */
public interface RecommendationStore {

    /** 묶음과 그 안의 코스들을 함께 보관한다 */
    void save(RecommendationSet recommendationSet);

    /** 코스 하나만 보관한다 (장소 교체로 새 버전이 생겼을 때, 6단계) */
    void saveCourse(RecommendedCourse course);

    /** 묶음 찾기. 없거나 이미 정리됐으면 빈 값 */
    Optional<RecommendationSet> findSet(String recommendationSetId);

    /** 코스 찾기. 없거나 이미 정리됐으면 빈 값 */
    Optional<RecommendedCourse> findCourse(String recommendationId);
}
