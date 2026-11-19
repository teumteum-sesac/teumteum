package com.teumteum.recommend.service;

import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * 후보 장소 목록으로 이용 시간 안에 들어가는 코스를 최대 3개 만든다 (REQ-019, REQ-020, REQ-024, NFR-014, NFR-024).
 * <p>
 * 두 단계로 나눈다.
 * <pre>
 * 1단계 고르기   (API 호출 없음): 직선거리로 어림하면서 장소를 고르고 방문 순서를 정한다
 * 2단계 확정하기 (API 호출)     : 고른 코스만 이동시간 API 로 정확히 계산한다
 *                                 → 외부 API 무료 호출 한도를 아끼려고 최종 후보에만 부른다 (NFR-014)
 * </pre>
 * 장소 고르기 규칙 (나혜빈 결정, 2026-11-17)
 * ① 직접 선택: 고른 대분류를 번갈아 넣는다. 코스마다 시작 대분류를 바꿔 다양하게 만든다
 * ② 한 코스 안에 같은 세분류는 두 번 넣지 않는다 (카페 → 카페 방지)
 * ③ 한 곳씩 넣으면서 어림한 총 소요시간이 이용 시간 안이면 계속, 들어갈 장소가 없으면 멈춘다
 * ④ 방문 순서는 출발지에서 가장 가까운 곳 → 그다음 가까운 곳
 * ⑤ 실제 이동시간으로 이용 시간을 넘으면 마지막 장소를 빼고 다시 확인, 다 빠지면 그 코스는 버린다
 * ⑥ 목표에 가까운 후보를 먼저 본다 (⑫). 완전 무작위로 섞으면 엉뚱한 곳이 자주 뽑혀서,
 *    "목표와 어긋난 거리 + 무작위 거리" 순으로 줄 세운다. 목표에 맞을수록 앞에 오지만 순서가 매번 조금씩 달라진다 (#170, #204)
 * ⑦ 한 구간 이동시간(직선 어림)이 이동수단 기준(TransportType.maxLegMinutes)을 넘는 장소는 넣지 않는다 (#170)
 * ⑧ 경로 모드 (도착지가 1km 이상 멀 때, #170): "가는 길에 들르는" 코스
 *    - 가까움 기준을 출발지 거리 대신 우회 거리(출발→장소 + 장소→도착 − 출발→도착)로 본다
 *    - 우회 거리가 기준보다 큰 장소(가는 길에서 많이 벗어난 곳)는 넣지 않는다
 *    - 방문 순서는 ④ 대신 "도착지 쪽으로 점점 가까워지는 순"
 * ⑨ 음식/맛집·카페/디저트는 한 코스에 1곳만 (한식 → 일식, 카페 → 디저트 X / 한식 → 카페 O) (#170)
 * ⑩ 같은 브랜드(체인)는 두 번 넣지 않는다 (메가커피 A점 → 메가커피 B점 X) (#170)
 * ⑪ 고를 때는 최소 체류시간으로 어림하고, 확정 뒤 남는 시간을 체류시간으로 채운다 (#204, StayTimeFiller)
 *    코스마다 채우는 목표를 이용 시간의 80% / 90% / 100% 로 달리해서 세 코스의 시간이 서로 다르게 나온다
 * ⑫ 거리 비율 (#204): 코스마다 "어디쯤 있는 장소" 를 고를지 목표를 다르게 준다
 *    - 보통      : 출발지에서 목표 거리 = 최대 반경 × 15% / 40% / 70% (가까운 코스 · 중간 코스 · 조금 먼 코스)
 *    - 경로 모드 : 출발지(0) ~ 도착지(1) 사이 목표 위치를 장소 자리마다 나눈다 (2곳이면 0.33 · 0.67)
 *                  → 장소가 가는 길을 따라 고르게 퍼진다. 코스마다 −0.1 / 0 / +0.1 씩 밀어 세 코스가 다르게 나온다
 *    목표는 "먼저 볼 순서" 일 뿐 걸러 내는 조건이 아니다. 목표 근처에 맞는 장소가 없으면 다른 장소를 고른다
 * <p>
 * 장소 검색·반경 넓히기는 하지 않는다. 받은 후보 안에서만 고른다.
 */
@Component
public class CourseComposer {

    /** 한 번에 추천하는 코스 수 (REQ-028) */
    static final int MAX_COURSE_COUNT = 3;

    /**
     * 코스를 만들어 보는 최대 횟수.
     * 후보를 섞어서 고르기 때문에 같은 코스가 또 나올 수 있다. 무한 반복을 막는 안전장치.
     */
    static final int MAX_ATTEMPTS = 10;

    /**
     * 후보를 줄 세울 때 섞는 정도 (규칙 ⑥). 이동수단 최대 검색 반경에 이 비율을 곱한 거리만큼 섞는다.
     * 도보(2km) → 0.5km, 대중교통·차량(5km) → 1.25km.
     * 이 거리 안쪽 차이는 무작위로 섞이고, 목표에서 그보다 많이 어긋난 장소는 뒤로 밀린다.
     * 0 이면 항상 같은 순서(매번 같은 코스), 클수록 무작위에 가까워진다.
     */
    static final double RANDOM_SPREAD_RATIO = 0.25;

    /**
     * 도착지가 없을 때 코스마다 노리는 출발지 거리 = 최대 검색 반경 × 이 비율 (규칙 ⑫, 팀 결정 2026-11-19).
     * 도보(2km) → 0.3km / 0.8km / 1.4km, 대중교통·차량(5km) → 0.75km / 2km / 3.5km
     */
    static final List<Double> DISTANCE_RATIOS = List.of(0.15, 0.4, 0.7);

    /**
     * 경로 모드에서 코스마다 목표 위치를 미는 정도 (규칙 ⑫, 팀 결정 2026-11-19).
     * 1번째 코스는 출발지 쪽으로, 3번째 코스는 도착지 쪽으로 조금 치우친다.
     */
    static final List<Double> ROUTE_POSITION_SHIFTS = List.of(-0.1, 0.0, 0.1);

    /**
     * 도착지가 출발지에서 이만큼(km) 이상 떨어져 있으면 "경로 모드" 로 코스를 만든다 (규칙 ⑧).
     * 이보다 가까우면 출발지 근처를 둘러보고 도착지로 가는 것과 같아서, 도착지가 없을 때처럼 만든다.
     */
    static final double ROUTE_MODE_MIN_KM = 1.0;

    /**
     * 경로 모드에서 허용하는 우회 거리 = 이동수단 최대 검색 반경 × 이 비율 (규칙 ⑧).
     * 도보(2km) → 1km, 대중교통·차량(5km) → 2.5km
     */
    static final double DETOUR_LIMIT_RATIO = 0.5;

    /**
     * 한 코스에 1곳만 넣는 대분류 (규칙 ⑨). 밥을 두 번 먹거나 카페를 연달아 가는 코스는 어색하다 (팀 회의, 2026-11-19).
     * 음식/맛집만 골랐으면 그 코스는 1곳짜리가 된다 (고르지 않은 대분류는 넣지 않으므로).
     */
    static final Set<Category> ONCE_PER_COURSE = EnumSet.of(Category.FOOD, Category.CAFE);

    /**
     * 코스마다 이용 시간을 몇 %까지 채울지 (규칙 ⑪, 팀 결정 2026-11-19).
     * 1번째 코스 80%, 2번째 90%, 3번째 100%. 예) 2시간 → 약 1:36 / 1:48 / 2:00
     * 모두 100%로 채우면 세 코스의 총 시간이 다 같아져서, 일부러 차이를 둔다.
     */
    static final List<Double> TIME_FILL_RATIOS = List.of(0.8, 0.9, 1.0);

    private final PlaceCountCalculator placeCountCalculator;
    private final StayTimeCalculator stayTimeCalculator;
    private final StraightLineTravelCalculator straightLineTravelCalculator;
    private final CourseTimeCalculator courseTimeCalculator;
    private final TravelTimeService travelTimeService;
    private final StayTimeFiller stayTimeFiller;

    /** 후보 줄 세우기에 섞는 무작위 값(규칙 ⑥). 테스트에서는 씨앗(seed)을 고정한 Random 을 넣어 결과를 매번 같게 만든다 */
    private final Random random;

    /**
     * 스프링이 쓰는 생성자. 생성자가 두 개라서 어느 것을 쓸지 @Autowired 로 알려준다.
     */
    @Autowired
    public CourseComposer(PlaceCountCalculator placeCountCalculator,
                          StayTimeCalculator stayTimeCalculator,
                          StraightLineTravelCalculator straightLineTravelCalculator,
                          CourseTimeCalculator courseTimeCalculator,
                          TravelTimeService travelTimeService,
                          StayTimeFiller stayTimeFiller) {
        this(placeCountCalculator, stayTimeCalculator, straightLineTravelCalculator,
                courseTimeCalculator, travelTimeService, stayTimeFiller, new Random());
    }

    /** 테스트용 생성자 (package-private). 섞는 순서를 고정하려고 Random 을 밖에서 받는다 */
    CourseComposer(PlaceCountCalculator placeCountCalculator,
                   StayTimeCalculator stayTimeCalculator,
                   StraightLineTravelCalculator straightLineTravelCalculator,
                   CourseTimeCalculator courseTimeCalculator,
                   TravelTimeService travelTimeService,
                   StayTimeFiller stayTimeFiller,
                   Random random) {
        this.placeCountCalculator = placeCountCalculator;
        this.stayTimeCalculator = stayTimeCalculator;
        this.straightLineTravelCalculator = straightLineTravelCalculator;
        this.courseTimeCalculator = courseTimeCalculator;
        this.travelTimeService = travelTimeService;
        this.stayTimeFiller = stayTimeFiller;
        this.random = random;
    }

    /**
     * 서로 다른 코스를 최대 3개 만든다.
     *
     * @param candidates 후보 장소 (장소 도메인에서 받은 것)
     * @return 완성된 코스 0 ~ 3개. 만들 수 있는 코스가 없으면 빈 목록 (오류 아님, REQ-027)
     */
    public List<CoursePlan> compose(CourseCondition condition, List<PlaceCandidate> candidates) {
        return compose(condition, candidates, List.of());
    }

    /**
     * 이미 찾은 코스가 있을 때, 모자란 만큼만 새 코스를 더 만든다 (반경을 넓혀 다시 찾을 때 사용).
     * <p>
     * 넓은 반경 후보에는 좁은 반경 후보가 다 들어 있어서 같은 코스가 또 나올 수 있다.
     * 이미 찾은 코스의 장소 묶음은 처음부터 "해 본 묶음" 으로 넣어 두어 API 를 다시 부르지 않는다.
     *
     * @param alreadyFound 앞에서 이미 찾은 코스 (이것과 같은 코스는 만들지 않는다)
     * @return 새로 만든 코스만. alreadyFound 와 합쳐서 최대 3개가 되도록 만든다
     */
    public List<CoursePlan> compose(CourseCondition condition, List<PlaceCandidate> candidates,
                                    List<CoursePlan> alreadyFound) {
        List<CoursePlan> courses = new ArrayList<>();
        int needed = MAX_COURSE_COUNT - alreadyFound.size();
        if (candidates == null || candidates.isEmpty() || needed <= 0) {
            return courses;
        }

        int maxPlaceCount = placeCountCalculator.calculateMaxPlaceCount(condition.availableMinutes());
        Set<Set<String>> triedPlaceSets = new HashSet<>(); // 이미 만들어 본 장소 묶음 (API 를 두 번 부르지 않게)
        for (CoursePlan found : alreadyFound) {
            triedPlaceSets.add(found.placeIds());
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS && courses.size() < needed; attempt++) {
            int courseIndex = alreadyFound.size() + courses.size(); // 이 코스가 전체에서 몇 번째인지 (0부터)

            // 1단계: 자리마다 목표 거리·위치에 가까운 후보부터 보며 고르기 (API 호출 없음, 규칙 ⑥·⑫)
            List<PlaceCandidate> picked = pickPlaces(condition, candidates, maxPlaceCount, attempt, courseIndex);

            if (picked.isEmpty() || !triedPlaceSets.add(placeIdsOf(picked))) {
                continue; // 고를 게 없거나, 이미 해 본 묶음이면 건너뜀 (Set.add 는 이미 있으면 false)
            }

            // 2단계: 고른 코스만 실제 이동시간으로 확정
            confirmCourse(condition, picked)
                    // 마지막 장소를 빼다가 기존 코스와 같아질 수 있음
                    .filter(course -> isNewCourse(courses, course) && isNewCourse(alreadyFound, course))
                    // 3단계: 남는 시간을 체류시간으로 채우기 (API 호출 없음, 규칙 ⑪)
                    .map(course -> stayTimeFiller.fill(course, fillTargetMinutes(condition, courseIndex)))
                    .ifPresent(courses::add);
        }
        return courses;
    }

    // ===================== 1단계: 고르기 (직선거리 어림) =====================

    /**
     * 이번 자리에 넣을 후보를 "목표에 맞는 곳이 앞에 오도록, 하지만 매번 조금 다르게" 줄 세운다 (규칙 ⑥·⑫).
     * <pre>
     * 점수 = 목표와 어긋난 거리(km, missKm) + 무작위(0 ~ 섞는 거리)     도보면 섞는 거리 0.5km
     * 예) 도착지 없음, 도보, 2번째 코스(목표 0.8km)
     *     0.7km 카페 → 어긋남 0.1 → 0.1 ~ 0.6
     *     1.0km 공원 → 어긋남 0.2 → 0.2 ~ 0.7   → 둘이 자주 자리를 바꾼다 (코스가 다양해짐)
     *     0.1km 책방 → 어긋남 0.7 → 0.7 ~ 1.2   → 거의 항상 뒤
     * </pre>
     * 어긋난 거리에 곱하지 않고 더하는 이유: 곱하면(× 1 ~ 2) 목표에 가장 맞는 한두 곳이 늘 1등이라
     * 같은 장소만 계속 나온다.
     * <p>
     * 점수를 후보마다 한 번만 뽑아 Map 에 넣어 둔다. 정렬 중에 비교할 때마다 새로 뽑으면 순서가 뒤엉킨다.
     *
     * @param courseIndex 몇 번째 코스인지 (0부터). 코스마다 목표 거리·위치가 다르다
     * @param slot        코스 안에서 몇 번째 자리인지 (0부터). 경로 모드에서 자리마다 목표 위치가 다르다
     * @param slotCount   이 코스의 장소 수 상한
     */
    List<PlaceCandidate> orderForSlot(CourseCondition condition, List<PlaceCandidate> candidates,
                                      int courseIndex, int slot, int slotCount) {
        double spreadKm = maxSearchRadiusKm(condition) * RANDOM_SPREAD_RATIO;
        Map<String, Double> scores = new HashMap<>();
        for (PlaceCandidate candidate : candidates) {
            double miss = missKm(condition, candidate, courseIndex, slot, slotCount);
            scores.put(candidate.placeId(), miss + random.nextDouble() * spreadKm);
        }

        List<PlaceCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(candidate -> scores.get(candidate.placeId())));
        return ordered;
    }

    /**
     * 후보가 이번 목표에서 얼마나 어긋났는지(km). 작을수록 목표에 맞는다 (규칙 ⑫).
     * <pre>
     * 보통      : | 출발지 → 장소 거리 − 목표 거리 |
     * 경로 모드 : | 경로 위 위치 − 목표 위치 | × 출발지 → 도착지 거리  +  우회 거리
     *             위치 차이(0 ~ 1)에 경로 길이를 곱해 km 로 바꾼다. 그래야 우회 거리(km)와 더할 수 있다
     *             우회 거리를 더해서, 위치가 맞아도 길에서 많이 벗어난 곳은 뒤로 밀린다
     * </pre>
     */
    double missKm(CourseCondition condition, PlaceCandidate candidate, int courseIndex, int slot, int slotCount) {
        if (!isRouteMode(condition)) {
            double distance = distanceKm(condition.origin(), coordinateOf(candidate));
            return Math.abs(distance - targetDistanceKm(condition, courseIndex));
        }
        double routeKm = distanceKm(condition.origin(), condition.destination());
        double positionGap = Math.abs(routePosition(condition, candidate) - targetPosition(courseIndex, slot, slotCount));
        return positionGap * routeKm + closenessKm(condition, candidate);
    }

    /** 도착지가 없을 때 이 코스가 노리는 출발지 거리(km) = 최대 검색 반경 × 15% / 40% / 70% */
    double targetDistanceKm(CourseCondition condition, int courseIndex) {
        return maxSearchRadiusKm(condition) * DISTANCE_RATIOS.get(ratioIndex(courseIndex, DISTANCE_RATIOS));
    }

    /**
     * 경로 모드에서 이 자리가 노리는 위치 (0 = 출발지, 1 = 도착지).
     * <pre>
     * 장소를 slotCount 곳 넣는다면 경로를 (slotCount + 1) 칸으로 나눈 경계에 하나씩 둔다
     * 1곳: 0.5 / 2곳: 0.33 · 0.67 / 3곳: 0.25 · 0.5 · 0.75 / 4곳: 0.2 · 0.4 · 0.6 · 0.8
     * 여기에 코스마다 −0.1 / 0 / +0.1 을 더한다. 0 ~ 1 을 벗어나면 끝에 맞춘다
     * </pre>
     */
    double targetPosition(int courseIndex, int slot, int slotCount) {
        double base = (slot + 1.0) / (slotCount + 1.0);
        double shift = ROUTE_POSITION_SHIFTS.get(ratioIndex(courseIndex, ROUTE_POSITION_SHIFTS));
        return Math.max(0.0, Math.min(1.0, base + shift));
    }

    /**
     * 장소가 경로 위 어디쯤인지 (0 = 출발지, 1 = 도착지).
     * 위치 = 출발지 → 장소 ÷ (출발지 → 장소 + 장소 → 도착지)
     * 예) 출발지에서 1km, 도착지까지 3km → 0.25 (출발지 쪽 1/4 지점)
     * 길에서 옆으로 벗어난 장소도 "출발지와 도착지 중 어느 쪽에 더 가까운지" 로 위치가 나온다.
     */
    double routePosition(CourseCondition condition, PlaceCandidate candidate) {
        Coordinate place = coordinateOf(candidate);
        double fromOrigin = distanceKm(condition.origin(), place);
        double toDestination = distanceKm(place, condition.destination());
        double sum = fromOrigin + toDestination;
        return sum == 0 ? 0 : fromOrigin / sum;
    }

    /** 비율 목록에서 몇 번째 값을 쓸지. 코스가 3개를 넘어도 마지막 값을 쓴다 */
    private int ratioIndex(int courseIndex, List<Double> ratios) {
        return Math.min(courseIndex, ratios.size() - 1);
    }

    /**
     * 도착지가 출발지에서 1km 이상 멀면 경로 모드 (규칙 ⑧).
     * 장소 검색(CourseSearchService)도 이 메서드로 판단해서, 검색과 코스 조합이 같은 기준을 쓴다.
     */
    public boolean isRouteMode(CourseCondition condition) {
        return condition.hasDestination()
                && distanceKm(condition.origin(), condition.destination()) >= ROUTE_MODE_MIN_KM;
    }

    /**
     * 이 후보가 얼마나 "가까운지"(km). 작을수록 가깝다.
     * <pre>
     * 보통       : 출발지 → 장소 거리
     * 경로 모드  : 우회 거리 = 출발지 → 장소 + 장소 → 도착지 − 출발지 → 도착지
     *              가는 길 위에 있으면 0 에 가깝고, 벗어날수록 커진다 (출발지에서 멀어도 길 위면 가깝다고 본다)
     * </pre>
     */
    double closenessKm(CourseCondition condition, PlaceCandidate candidate) {
        Coordinate place = coordinateOf(candidate);
        if (!isRouteMode(condition)) {
            return distanceKm(condition.origin(), place);
        }
        return distanceKm(condition.origin(), place)
                + distanceKm(place, condition.destination())
                - distanceKm(condition.origin(), condition.destination());
    }

    /** 경로 모드에서 가는 길을 너무 벗어나지 않았는지 (규칙 ⑧). 경로 모드가 아니면 항상 true */
    private boolean isOnTheWay(CourseCondition condition, PlaceCandidate candidate) {
        return !isRouteMode(condition)
                || closenessKm(condition, candidate) <= maxSearchRadiusKm(condition) * DETOUR_LIMIT_RATIO;
    }

    /**
     * 장소 수 상한 안에서 한 곳씩 고른다. 반환 목록은 이미 방문 순서대로 정렬되어 있다.
     * 자리마다 후보를 새로 줄 세운다 (경로 모드에서는 자리마다 목표 위치가 달라서, 규칙 ⑫).
     *
     * @param candidates  후보 전체. 자리마다 orderForSlot 으로 줄 세운 뒤 앞에서부터 조건에 맞는 첫 장소를 고른다
     * @param attempt     몇 번째 시도인지. 직접 선택일 때 시작 대분류를 바꾸는 데 쓴다 (규칙 ①)
     * @param courseIndex 몇 번째 코스인지 (0부터). 목표 거리·위치를 정하는 데 쓴다 (규칙 ⑫)
     */
    List<PlaceCandidate> pickPlaces(CourseCondition condition, List<PlaceCandidate> candidates,
                                    int maxPlaceCount, int attempt, int courseIndex) {
        List<PlaceCandidate> picked = new ArrayList<>();

        for (int slot = 0; slot < maxPlaceCount; slot++) {
            List<PlaceCandidate> ordered = orderForSlot(condition, candidates, courseIndex, slot, maxPlaceCount);
            Optional<PlaceCandidate> next = findPlaceThatFits(condition, ordered, picked, slot + attempt);
            if (next.isEmpty()) {
                break; // 더 넣을 수 있는 장소가 없음 → 지금까지 고른 만큼만 (규칙 ③)
            }
            picked.add(next.get());
            picked = orderForVisit(condition, picked); // 넣을 때마다 방문 순서 다시 정하기 (규칙 ④·⑧)
        }
        return picked;
    }

    /**
     * 이번 자리에 넣을 장소 하나를 찾는다.
     * 원하는 대분류부터 찾고, 없으면 나머지 고른 대분류에서 찾는다 (그 대분류 후보가 바닥났을 때).
     */
    private Optional<PlaceCandidate> findPlaceThatFits(CourseCondition condition, List<PlaceCandidate> shuffled,
                                                       List<PlaceCandidate> picked, int turn) {
        for (Category category : categoryOrder(condition, turn)) {
            for (PlaceCandidate candidate : shuffled) {
                if (!canAdd(candidate, category, picked) || !isOnTheWay(condition, candidate)) {
                    continue;
                }
                List<PlaceCandidate> tried = withPlace(picked, candidate);
                if (estimateTotalMinutes(condition, tried) <= condition.availableMinutes()
                        && isWithinLegLimit(condition, tried)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 모든 구간이 이동수단의 한 구간 최대 이동시간(직선 어림) 안인지 (규칙 ⑦).
     * 예) 도보 최대 25분: 출발지 → 카페 10분 → 공원 30분 이면 false (공원은 넣지 않는다)
     * <p>
     * 마지막 장소 → 도착지 구간은 따지지 않는다. 출발지와 도착지가 원래 멀면 그 구간은 길 수밖에 없어서,
     * 여기에 기준을 걸면 도착지가 먼 조건에서는 코스가 하나도 안 나온다.
     */
    boolean isWithinLegLimit(CourseCondition condition, List<PlaceCandidate> places) {
        int maxLegMinutes = condition.transportType().getMaxLegMinutes();
        Coordinate from = condition.origin();
        for (PlaceCandidate place : orderForVisit(condition, places)) {
            if (straightLineMinutes(condition, from, coordinateOf(place)) > maxLegMinutes) {
                return false;
            }
            from = coordinateOf(place);
        }
        return true;
    }

    /**
     * 이번 차례에 찾아볼 대분류 순서 (규칙 ①).
     * 예) 고른 대분류 [카페, 음식], turn 0 → [카페, 음식] / turn 1 → [음식, 카페] / turn 2 → [카페, 음식]
     * 랜덤이면 대분류를 따지지 않으므로 null 하나만 담는다 (null = 아무 대분류나).
     */
    private List<Category> categoryOrder(CourseCondition condition, int turn) {
        if (condition.isRandom()) {
            return Collections.singletonList(null); // List.of(null) 은 null 을 못 담아서 이걸 쓴다
        }
        List<Category> categories = condition.categories();
        List<Category> order = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            order.add(categories.get((turn + i) % categories.size())); // % 로 끝에 가면 처음으로 돌아감
        }
        return order;
    }

    /**
     * 이 후보를 코스에 넣어도 되는지.
     * - 이미 넣은 장소가 아니어야 하고
     * - 원하는 대분류여야 하고 (category 가 null 이면 아무거나)
     * - 이미 넣은 장소와 세분류가 겹치지 않아야 한다 (규칙 ②)
     * - 음식/맛집·카페/디저트는 한 코스에 1곳만 (규칙 ⑨)
     * - 같은 브랜드(체인)는 두 번 넣지 않는다 (규칙 ⑩)
     */
    private boolean canAdd(PlaceCandidate candidate, Category category, List<PlaceCandidate> picked) {
        if (category != null && candidate.category() != category) {
            return false;
        }
        for (PlaceCandidate place : picked) {
            if (place.placeId().equals(candidate.placeId())
                    || Objects.equals(place.subCategory(), candidate.subCategory()) // null 이어도 안전하게 비교
                    || isOncePerCourseCategoryTwice(place, candidate)
                    || brandOf(place.placeName()).equals(brandOf(candidate.placeName()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 음식/맛집 또는 카페/디저트가 두 번째로 들어가려는지 (규칙 ⑨).
     * 예) 한식 → 일식 (밥 두 번), 카페 → 디저트 (카페 두 번) 은 막고, 한식 → 카페 는 괜찮다.
     */
    private boolean isOncePerCourseCategoryTwice(PlaceCandidate place, PlaceCandidate candidate) {
        return place.category() == candidate.category() && ONCE_PER_COURSE.contains(candidate.category());
    }

    /**
     * 장소 이름에서 지점명을 뗀 브랜드 이름 (규칙 ⑩).
     * <pre>
     * "메가MGC커피 성수역점"       → "메가MGC커피"
     * "프랑스루브르바게트 성수점"  → "프랑스루브르바게트"
     * "성수동구두테마공원"         → "성수동구두테마공원"   (지점명 없음 → 그대로)
     * </pre>
     * 카카오 장소 이름은 대개 "브랜드 지점명점" 모양이라, 마지막 단어가 '점' 으로 끝나면 지점명으로 보고 뗀다.
     */
    String brandOf(String placeName) {
        if (placeName == null) {
            return "";
        }
        String name = placeName.trim();
        int lastSpace = name.lastIndexOf(' ');
        if (lastSpace > 0 && name.endsWith("점")) {
            return name.substring(0, lastSpace).trim();
        }
        return name;
    }

    /**
     * 직선거리로 어림한 총 소요시간 = 체류 합 + 이동 합 (+ 도착지가 있으면 마지막 → 도착지).
     * 넣을지 말지 판단만 하는 값이라 API 를 부르지 않는다.
     */
    int estimateTotalMinutes(CourseCondition condition, List<PlaceCandidate> places) {
        List<PlaceCandidate> ordered = orderForVisit(condition, places);
        int total = 0;
        Coordinate from = condition.origin();

        for (PlaceCandidate place : ordered) {
            total += straightLineMinutes(condition, from, coordinateOf(place));
            total += stayTimeCalculator.calculate(place.subCategory());
            from = coordinateOf(place);
        }
        if (condition.hasDestination()) {
            total += straightLineMinutes(condition, from, condition.destination());
        }
        return total;
    }

    /**
     * 방문 순서 정하기.
     * <pre>
     * 보통      : 지금 위치에서 가장 가까운 곳 → 그다음 가까운 곳 (규칙 ④, orderByNearest)
     * 경로 모드 : 도착지 쪽으로 점점 가까워지는 순 (규칙 ⑧)
     *             점수 = 출발지 → 장소 − 장소 → 도착지. 작을수록 출발지 쪽이라 먼저 간다
     * </pre>
     * 경로 모드에서 ④를 쓰면 출발지에서 조금 더 가까운 곳을 먼저 가느라 도착지 쪽으로 갔다가 되돌아오는 순서가 나올 수 있다.
     */
    List<PlaceCandidate> orderForVisit(CourseCondition condition, List<PlaceCandidate> places) {
        if (!isRouteMode(condition)) {
            return orderByNearest(condition.origin(), places);
        }
        List<PlaceCandidate> ordered = new ArrayList<>(places);
        ordered.sort(Comparator.comparingDouble(place ->
                distanceKm(condition.origin(), coordinateOf(place)) - distanceKm(coordinateOf(place), condition.destination())));
        return ordered;
    }

    /**
     * 방문 순서 정하기: 지금 위치에서 가장 가까운 곳을 다음으로 간다 (규칙 ④, 최근접 이웃 방식).
     * 장소가 최대 4곳이라 모든 순서를 다 따져 보지 않아도 충분히 괜찮은 순서가 나온다.
     */
    List<PlaceCandidate> orderByNearest(Coordinate origin, List<PlaceCandidate> places) {
        List<PlaceCandidate> remaining = new ArrayList<>(places);
        List<PlaceCandidate> ordered = new ArrayList<>();
        Coordinate current = origin;

        while (!remaining.isEmpty()) {
            PlaceCandidate nearest = remaining.get(0);
            for (PlaceCandidate place : remaining) {
                if (distanceKm(current, coordinateOf(place)) < distanceKm(current, coordinateOf(nearest))) {
                    nearest = place;
                }
            }
            ordered.add(nearest);
            remaining.remove(nearest);
            current = coordinateOf(nearest);
        }
        return ordered;
    }

    // ===================== 2단계: 확정하기 (이동시간 API) =====================

    /**
     * 고른 코스를 이동시간 서비스로 정확히 계산한다.
     * 이용 시간을 넘으면 마지막 장소를 하나씩 빼면서 다시 확인한다 (규칙 ⑤).
     * <p>
     * 장소를 빼도 앞 구간들의 이동시간은 그대로라 다시 부르지 않는다.
     * 바뀌는 건 "마지막 장소 → 도착지" 구간뿐이다 (NFR-014).
     *
     * @param places 방문 순서대로 정렬된 장소
     */
    Optional<CoursePlan> confirmCourse(CourseCondition condition, List<PlaceCandidate> places) {
        List<CourseStop> stops = new ArrayList<>();
        Coordinate from = condition.origin();
        for (PlaceCandidate place : places) {
            TravelRoute travel = findRoute(condition, from, coordinateOf(place));
            stops.add(new CourseStop(place, stayTimeCalculator.calculate(place.subCategory()), travel));
            from = coordinateOf(place);
        }

        for (int count = stops.size(); count > 0; count--) {
            List<CourseStop> kept = stops.subList(0, count); // 앞에서부터 count 곳만
            TravelRoute finalTravel = condition.hasDestination()
                    ? findRoute(condition, coordinateOf(kept.get(count - 1).place()), condition.destination())
                    : null;

            CoursePlan course = courseTimeCalculator.toCoursePlan(kept, finalTravel);
            if (courseTimeCalculator.isWithinAvailableTime(course.totalMinutes(), condition.availableMinutes())) {
                return Optional.of(course);
            }
        }
        return Optional.empty(); // 한 곳만 남겨도 넘음 → 이 코스는 버린다
    }

    /**
     * 몇 번째 코스인지에 따라 채울 목표 총 소요시간(분) (규칙 ⑪).
     * 예) 이용 시간 120분: 0번째 → 96분, 1번째 → 108분, 2번째 → 120분
     * 소수점은 버린다 (올리면 이용 시간을 1분 넘을 수 있다).
     */
    int fillTargetMinutes(CourseCondition condition, int courseIndex) {
        int index = Math.min(courseIndex, TIME_FILL_RATIOS.size() - 1); // 혹시 3개를 넘어도 마지막 비율
        return (int) Math.floor(condition.availableMinutes() * TIME_FILL_RATIOS.get(index));
    }

    // ===================== 작은 도우미들 =====================

    private TravelRoute findRoute(CourseCondition condition, Coordinate from, Coordinate to) {
        return travelTimeService.findRoute(condition.transportType(),
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private int straightLineMinutes(CourseCondition condition, Coordinate from, Coordinate to) {
        return straightLineTravelCalculator.calculateTravelMinutes(condition.transportType(),
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private double distanceKm(Coordinate from, Coordinate to) {
        return straightLineTravelCalculator.calculateDistanceKm(
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private double maxSearchRadiusKm(CourseCondition condition) {
        return condition.transportType().getMaxSearchRadiusMeters() / 1000.0;
    }

    private Coordinate coordinateOf(PlaceCandidate place) {
        return new Coordinate(place.latitude(), place.longitude());
    }

    /** 원래 목록은 그대로 두고, 장소 하나를 더한 새 목록을 만든다 (넣어 보고 시간만 재 보려고) */
    private List<PlaceCandidate> withPlace(List<PlaceCandidate> places, PlaceCandidate place) {
        List<PlaceCandidate> result = new ArrayList<>(places);
        result.add(place);
        return result;
    }

    private Set<String> placeIdsOf(List<PlaceCandidate> places) {
        Set<String> ids = new HashSet<>();
        for (PlaceCandidate place : places) {
            ids.add(place.placeId());
        }
        return ids;
    }

    private boolean isNewCourse(List<CoursePlan> courses, CoursePlan course) {
        for (CoursePlan existing : courses) {
            if (existing.placeIds().equals(course.placeIds())) {
                return false;
            }
        }
        return true;
    }
}
