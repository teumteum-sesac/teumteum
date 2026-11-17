package com.teumteum.recommend.repository;

import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 추천 결과를 서버 메모리(Map)에 보관한다 (나혜빈 결정, 2026-11-17).
 * <p>
 * - 서버를 껐다 켜면 전부 사라진다 → 그 ID 로 들어오면 404 (명세에 적어 둠)
 * - 만료된 결과는 바로 지우지 않고 1시간 더 남겨 둔다 → 그동안은 서비스가 410(만료)으로 응답할 수 있다
 * - 정리는 따로 돌지 않고 "새로 저장할 때" 같이 한다 → 스케줄러 설정(@EnableScheduling)이 필요 없다
 * <p>
 * ConcurrentHashMap 을 쓰는 이유: 웹 서버는 요청 여러 개를 동시에(여러 스레드로) 처리한다.
 * 보통 HashMap 은 동시에 넣고 지우면 내용이 깨질 수 있어서, 동시에 써도 안전한 ConcurrentHashMap 을 쓴다.
 */
@Repository
public class InMemoryRecommendationStore implements RecommendationStore {

    /** 만료 후 정리하기 전까지 더 남겨 두는 시간. 이 시간 동안은 404 대신 410 을 줄 수 있다 */
    static final Duration CLEANUP_GRACE = Duration.ofHours(1);

    private final Map<String, RecommendationSet> sets = new ConcurrentHashMap<>();
    private final Map<String, RecommendedCourse> courses = new ConcurrentHashMap<>();

    /** 지금 시각을 알려주는 시계. 테스트에서는 시각을 고정한 시계를 넣어 "3시간 뒤" 같은 상황을 만든다 */
    private final Clock clock;

    /** 스프링이 쓰는 생성자 (실제 시계) */
    public InMemoryRecommendationStore() {
        this(Clock.systemDefaultZone());
    }

    /** 테스트용 생성자 (package-private) */
    InMemoryRecommendationStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(RecommendationSet recommendationSet) {
        removeOld();
        sets.put(recommendationSet.recommendationSetId(), recommendationSet);
        for (RecommendedCourse course : recommendationSet.courses()) {
            courses.put(course.recommendationId(), course);
        }
    }

    @Override
    public void saveCourse(RecommendedCourse course) {
        removeOld();
        courses.put(course.recommendationId(), course);
    }

    @Override
    public Optional<RecommendationSet> findSet(String recommendationSetId) {
        // Map.get 은 없으면 null → Optional.ofNullable 로 감싸서 "없을 수 있음" 을 드러낸다
        return Optional.ofNullable(sets.get(recommendationSetId));
    }

    @Override
    public Optional<RecommendedCourse> findCourse(String recommendationId) {
        return Optional.ofNullable(courses.get(recommendationId));
    }

    /**
     * 만료 후 1시간이 지난 결과를 지운다.
     * removeIf: 조건에 맞는 항목을 한 번에 지운다. 반복문을 돌면서 직접 remove 하면 오류가 날 수 있어 이걸 쓴다.
     */
    void removeOld() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        sets.values().removeIf(set -> now.isAfter(set.expiresAt().plus(CLEANUP_GRACE)));
        courses.values().removeIf(course -> now.isAfter(course.expiresAt().plus(CLEANUP_GRACE)));
    }

    /** 테스트에서 지금 몇 개가 남아 있는지 확인하는 용도 */
    int courseCount() {
        return courses.size();
    }
}
