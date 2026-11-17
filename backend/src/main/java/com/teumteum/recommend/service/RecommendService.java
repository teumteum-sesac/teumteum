package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.RecommendCondition;
import com.teumteum.recommend.domain.RecommendCondition.Location;
import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import com.teumteum.recommend.dto.LocationRequest;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.dto.RecommendRequest;
import com.teumteum.recommend.dto.RecommendResponse;
import com.teumteum.recommend.exception.RecommendErrorCode;
import com.teumteum.recommend.repository.RecommendationStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 추천 생성 (POST /api/recommendations, recommend-api.md 1장).
 * <pre>
 * 1. 요청 → 서버가 적용할 조건으로 정리 (DIRECT + 빈 대분류 → RANDOM, 중복 정리)
 * 2. 코스 찾기 (CourseSearchService: 반경 넓히기 + 코스 조합 + 이동시간)
 * 3. ID 붙여서 2시간 보관 (rs_ 묶음, rc_ 코스)
 * 4. 응답 DTO 로 변환
 * </pre>
 * 코스가 하나도 없어도 묶음은 보관하고 200 + courses [] 로 응답한다 (REQ-027).
 * 장소 검색 외부 API 오류(502·503)는 CourseSearchService 가 던진 그대로 전달된다.
 */
@Service
public class RecommendService {

    /** 추천 결과 보관 시간 (REQ-056) */
    static final Duration RETENTION = Duration.ofHours(2);

    /** 만료 시각을 "+09:00" 한국 시간으로 내보내려고 서울 시간대를 쓴다 (배포 서버 시간대가 달라도 같게) */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CourseSearchService courseSearchService;
    private final RecommendationStore recommendationStore;
    private final RecommendResponseMapper responseMapper;
    private final Clock clock;

    /** 스프링이 쓰는 생성자 (실제 서울 시계) */
    @Autowired
    public RecommendService(CourseSearchService courseSearchService,
                            RecommendationStore recommendationStore,
                            RecommendResponseMapper responseMapper) {
        this(courseSearchService, recommendationStore, responseMapper, Clock.system(SEOUL));
    }

    /** 테스트용 생성자 (package-private). 시각을 고정해 만료 시각을 확인한다 */
    RecommendService(CourseSearchService courseSearchService,
                     RecommendationStore recommendationStore,
                     RecommendResponseMapper responseMapper,
                     Clock clock) {
        this.courseSearchService = courseSearchService;
        this.recommendationStore = recommendationStore;
        this.responseMapper = responseMapper;
        this.clock = clock;
    }

    public RecommendResponse recommend(RecommendRequest request) {
        RecommendCondition condition = toCondition(request);
        List<CoursePlan> plans = courseSearchService.findCourses(condition.toCourseCondition());

        RecommendationSet recommendationSet = createSet(condition, plans);
        recommendationStore.save(recommendationSet);

        return responseMapper.toResponse(recommendationSet);
    }

    /**
     * 추천 코스 상세 (GET /api/recommendations/courses/{recommendationId}, 명세 3장).
     * <p>
     * 코스 저장(course 도메인)도 이 메서드로 저장할 코스의 장소·일정·지도 데이터를 가져간다.
     * 다른 도메인은 보관소(repository)가 아니라 이 서비스를 통해 접근한다 (AGENTS.md 도메인 경계).
     *
     * @throws CustomException 없거나 정리된 ID → 404 RECOMMENDATION_NOT_FOUND, 2시간 지난 ID → 410 RECOMMENDATION_EXPIRED
     */
    public RecommendCourseDetailResponse getCourseDetail(String recommendationId) {
        RecommendedCourse course = recommendationStore.findCourse(recommendationId)
                .orElseThrow(() -> new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND));
        if (course.isExpiredAt(OffsetDateTime.now(clock))) {
            throw new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED);
        }

        // 출발지·이동수단 같은 조건은 묶음에 있다. 코스와 묶음은 같이 보관·정리되므로 보통 항상 있다
        RecommendationSet recommendationSet = recommendationStore.findSet(course.recommendationSetId())
                .orElseThrow(() -> new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND));

        return responseMapper.toCourseDetailResponse(course, recommendationSet.condition());
    }

    /** 요청 DTO → 조건 도메인. 정리 규칙은 RecommendCondition 생성자가 처리한다 */
    RecommendCondition toCondition(RecommendRequest request) {
        return new RecommendCondition(
                toLocation(request.origin()),
                request.destination() == null ? null : toLocation(request.destination()),
                request.startTime(),
                request.availableMinutes(),
                request.transportType(),
                request.categoryMode(),
                request.categories());
    }

    private Location toLocation(LocationRequest location) {
        return new Location(location.name(), location.address(), location.latitude(), location.longitude());
    }

    /** 코스마다 ID 를 붙이고 같은 만료 시각으로 묶는다 */
    private RecommendationSet createSet(RecommendCondition condition, List<CoursePlan> plans) {
        // 초 단위로 자른다: 응답이 "15:00:00+09:00" 처럼 나가게 (소수점 아래 나노초 제거)
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime expiresAt = now.plus(RETENTION);
        String recommendationSetId = newId("rs_");

        List<RecommendedCourse> courses = new ArrayList<>();
        for (CoursePlan plan : plans) {
            courses.add(new RecommendedCourse(newId("rc_"), recommendationSetId, plan, expiresAt));
        }
        return new RecommendationSet(recommendationSetId, condition, courses, now, expiresAt);
    }

    /**
     * 겹치지 않는 ID 를 만든다. 예) "rc_3f2a9c0e4b1d4e8f9a7b6c5d4e3f2a1b"
     * UUID 는 무작위로 만들어서 겹칠 확률이 사실상 0 이다. URL 에 넣기 좋게 '-' 는 뺀다.
     */
    private String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
