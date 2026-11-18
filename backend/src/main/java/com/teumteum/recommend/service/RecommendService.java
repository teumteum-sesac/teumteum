package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseSearchResult;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final RegenerateRateLimiter regenerateRateLimiter;
    private final Clock clock;

    /** 스프링이 쓰는 생성자 (실제 서울 시계) */
    @Autowired
    public RecommendService(CourseSearchService courseSearchService,
                            RecommendationStore recommendationStore,
                            RecommendResponseMapper responseMapper,
                            RegenerateRateLimiter regenerateRateLimiter) {
        this(courseSearchService, recommendationStore, responseMapper, regenerateRateLimiter, Clock.system(SEOUL));
    }

    /**
     * 테스트용 생성자 (package-private). 시각을 고정해 만료 시각을 확인한다.
     * 횟수 제한기도 같은 시계로 만든다 (제한 자체는 RegenerateRateLimiterTest 에서 따로 확인)
     */
    RecommendService(CourseSearchService courseSearchService,
                     RecommendationStore recommendationStore,
                     RecommendResponseMapper responseMapper,
                     Clock clock) {
        this(courseSearchService, recommendationStore, responseMapper, new RegenerateRateLimiter(clock), clock);
    }

    /** 실제로 필드를 채우는 생성자. 위 두 생성자가 이걸 부른다 */
    private RecommendService(CourseSearchService courseSearchService,
                             RecommendationStore recommendationStore,
                             RecommendResponseMapper responseMapper,
                             RegenerateRateLimiter regenerateRateLimiter,
                             Clock clock) {
        this.courseSearchService = courseSearchService;
        this.recommendationStore = recommendationStore;
        this.responseMapper = responseMapper;
        this.regenerateRateLimiter = regenerateRateLimiter;
        this.clock = clock;
    }

    public RecommendResponse recommend(RecommendRequest request) {
        RecommendCondition condition = toCondition(request);
        CourseSearchResult searchResult = courseSearchService.findCourses(condition.toCourseCondition());

        RecommendationSet recommendationSet = createSet(condition, searchResult);
        recommendationStore.save(recommendationSet);

        return responseMapper.toResponse(recommendationSet);
    }

    /**
     * 다시 추천 (POST /api/recommendations/{recommendationSetId}/regenerate, 명세 2장, REQ-026).
     * <pre>
     * 0. 횟수 제한 확인: 같은 사용자가 최근 1분에 5번 넘으면 429 (404·410 이 될 요청도 센다)
     * 1. 보관한 묶음 꺼내기 (없으면 404, 만료면 410)
     * 2. 같은 조건으로, 직전 묶음에 나온 장소를 빼고 코스 찾기
     * 3. 하나도 없으면 한 번만 빼지 않고 다시 찾기 ("가능한 한 제외", 겹치는 장소가 나올 수 있음)
     * 4. 새 묶음 ID(rs_)로 2시간 보관 → 응답 (생성과 같은 RecommendResponse)
     * </pre>
     * 직전 묶음은 지우지 않는다. 사용자가 뒤로 가서 예전 코스 상세를 볼 수 있다 (만료되면 자연히 정리).
     *
     * @param clientKey 횟수를 셀 사용자 구분 값 (컨트롤러가 넘긴 IP 주소)
     */
    public RecommendResponse regenerate(String recommendationSetId, String clientKey) {
        regenerateRateLimiter.check(clientKey); // 맨 먼저: 외부 API 를 부르기 전에 막는다

        RecommendationSet previous = findValidSet(recommendationSetId);
        RecommendCondition condition = previous.condition();

        CourseSearchResult searchResult =
                courseSearchService.findCourses(condition.toCourseCondition(), placeIdsIn(previous));
        if (searchResult.plans().isEmpty() && !previous.courses().isEmpty()) {
            searchResult = courseSearchService.findCourses(condition.toCourseCondition()); // 겹쳐도 되니 다시 찾기
        }

        RecommendationSet recommendationSet = createSet(condition, searchResult);
        recommendationStore.save(recommendationSet);
        return responseMapper.toResponse(recommendationSet);
    }

    /** 묶음 꺼내기. 없거나 정리됐으면 404, 2시간 지났으면 410 */
    private RecommendationSet findValidSet(String recommendationSetId) {
        RecommendationSet recommendationSet = recommendationStore.findSet(recommendationSetId)
                .orElseThrow(() -> new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND));
        if (recommendationSet.isExpiredAt(OffsetDateTime.now(clock))) {
            throw new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED);
        }
        return recommendationSet;
    }

    /** 묶음 안 모든 코스의 장소 ID (다시 추천 때 뺄 장소) */
    private Set<String> placeIdsIn(RecommendationSet recommendationSet) {
        Set<String> placeIds = new HashSet<>();
        for (RecommendedCourse course : recommendationSet.courses()) {
            placeIds.addAll(course.plan().placeIds());
        }
        return placeIds;
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
        RecommendedCourse course = findValidCourse(recommendationId);
        RecommendationSet recommendationSet = findSetOf(course);
        return responseMapper.toCourseDetailResponse(course, recommendationSet.condition());
    }

    /**
     * 코스 꺼내기. 없거나 정리됐으면 404, 2시간 지났으면 410.
     * 장소 교체(PlaceReplaceService)도 같은 규칙으로 코스를 꺼내므로 package-private 으로 열어 둔다.
     */
    RecommendedCourse findValidCourse(String recommendationId) {
        RecommendedCourse course = recommendationStore.findCourse(recommendationId)
                .orElseThrow(() -> new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND));
        if (course.isExpiredAt(OffsetDateTime.now(clock))) {
            throw new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED);
        }
        return course;
    }

    /**
     * 코스가 속한 묶음 꺼내기 (출발지·이동수단 같은 조건과 장소 후보가 묶음에 있다).
     * 코스와 묶음은 같이 보관·정리되므로 보통 항상 있다. 없으면 404.
     */
    RecommendationSet findSetOf(RecommendedCourse course) {
        return recommendationStore.findSet(course.recommendationSetId())
                .orElseThrow(() -> new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND));
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

    /** 코스마다 ID 를 붙이고 같은 만료 시각으로 묶는다. 장소 후보도 교체용으로 함께 보관한다 */
    private RecommendationSet createSet(RecommendCondition condition, CourseSearchResult searchResult) {
        // 초 단위로 자른다: 응답이 "15:00:00+09:00" 처럼 나가게 (소수점 아래 나노초 제거)
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime expiresAt = now.plus(RETENTION);
        String recommendationSetId = newId("rs_");

        List<RecommendedCourse> courses = new ArrayList<>();
        for (CoursePlan plan : searchResult.plans()) {
            courses.add(new RecommendedCourse(newId("rc_"), recommendationSetId, plan, expiresAt));
        }
        return new RecommendationSet(recommendationSetId, condition, courses, now, expiresAt,
                searchResult.candidates());
    }

    /**
     * 겹치지 않는 ID 를 만든다. 예) "rc_3f2a9c0e4b1d4e8f9a7b6c5d4e3f2a1b"
     * UUID 는 무작위로 만들어서 겹칠 확률이 사실상 0 이다. URL 에 넣기 좋게 '-' 는 뺀다.
     */
    private String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
