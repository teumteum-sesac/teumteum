package com.teumteum.recommend.repository;

import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.RecommendCondition;
import com.teumteum.recommend.domain.RecommendCondition.Location;
import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import com.teumteum.recommend.domain.TransportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 메모리 보관소 테스트.
 * <p>
 * 시계를 NOW 에 고정해 두고, 만료 시각을 과거로 잡은 결과를 넣어서 "시간이 지난 상황" 을 만든다.
 * (시간을 실제로 기다리지 않아도 된다)
 */
class InMemoryRecommendationStoreTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));

    private final InMemoryRecommendationStore store =
            new InMemoryRecommendationStore(Clock.fixed(NOW.toInstant(), ZoneOffset.ofHours(9)));

    @Test
    @DisplayName("묶음을 저장하면 묶음 ID 와 코스 ID 로 각각 찾을 수 있다")
    void save_thenFindBySetIdAndCourseId() {
        store.save(setOf("rs_1", NOW.plusHours(2), "rc_1", "rc_2"));

        assertTrue(store.findSet("rs_1").isPresent());
        assertTrue(store.findCourse("rc_1").isPresent());
        assertTrue(store.findCourse("rc_2").isPresent());
    }

    @Test
    @DisplayName("없는 ID 는 빈 값이다")
    void find_returnsEmpty_whenUnknownId() {
        assertTrue(store.findSet("rs_없음").isEmpty());
        assertTrue(store.findCourse("rc_없음").isEmpty());
    }

    @Test
    @DisplayName("만료됐어도 1시간 안이면 지우지 않는다 (서비스가 410 을 줄 수 있게)")
    void keepsExpired_withinGrace() {
        store.save(setOf("rs_old", NOW.minusMinutes(30), "rc_old")); // 30분 전에 만료
        store.save(setOf("rs_new", NOW.plusHours(2), "rc_new"));     // 새로 저장 → 이때 정리가 돈다

        assertTrue(store.findSet("rs_old").isPresent());
        assertTrue(store.findCourse("rc_old").isPresent());
    }

    @Test
    @DisplayName("만료 후 1시간이 지난 결과는 다음 저장 때 지워진다 (그 뒤엔 404)")
    void removesExpired_afterGrace() {
        store.save(setOf("rs_old", NOW.minusHours(2), "rc_old")); // 2시간 전에 만료
        store.save(setOf("rs_new", NOW.plusHours(2), "rc_new"));

        assertTrue(store.findSet("rs_old").isEmpty());
        assertTrue(store.findCourse("rc_old").isEmpty());
        assertTrue(store.findSet("rs_new").isPresent());
        assertEquals(1, store.courseCount());
    }

    @Test
    @DisplayName("코스 하나만 따로 저장할 수 있다 (장소 교체로 생긴 새 버전)")
    void saveCourse_storesSingleCourse() {
        store.saveCourse(courseOf("rc_replaced", "rs_1", NOW.plusHours(2)));

        assertTrue(store.findCourse("rc_replaced").isPresent());
    }

    // ===================== 도우미 =====================

    private RecommendationSet setOf(String setId, OffsetDateTime expiresAt, String... courseIds) {
        List<RecommendedCourse> courses = Arrays.stream(courseIds)
                .map(id -> courseOf(id, setId, expiresAt))
                .toList();
        return new RecommendationSet(setId, condition(), courses, expiresAt.minusHours(2), expiresAt);
    }

    private RecommendedCourse courseOf(String courseId, String setId, OffsetDateTime expiresAt) {
        CoursePlan emptyPlan = new CoursePlan(List.of(), null, 0, 0, false);
        return new RecommendedCourse(courseId, setId, emptyPlan, expiresAt);
    }

    private RecommendCondition condition() {
        return new RecommendCondition(new Location("성수역", null, 37.5446, 127.0559), null,
                "13:00", 180, TransportType.WALK, CategoryMode.RANDOM, List.of());
    }
}
