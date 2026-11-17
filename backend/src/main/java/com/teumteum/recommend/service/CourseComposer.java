package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.PlaceVisit;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * 후보 장소 목록으로 이용 시간 안에 들어가는 코스를 최대 3개 만든다 (REQ-019, REQ-020, REQ-024, NFR-014, NFR-024).
 * <p>
 * 두 단계로 나눈다.
 * <pre>
 * 1단계 고르기 (공짜)  : 직선거리로 어림하면서 장소를 고르고 방문 순서를 정한다 → 외부 API 호출 없음
 * 2단계 확정하기 (유료): 고른 코스만 이동시간 API 로 정확히 계산한다 → 최종 후보에만 호출 (NFR-014)
 * </pre>
 * 장소 고르기 규칙 (나혜빈 결정, 2026-11-17)
 * ① 직접 선택: 고른 대분류를 번갈아 넣는다. 코스마다 시작 대분류를 바꿔 다양하게 만든다
 * ② 한 코스 안에 같은 세분류는 두 번 넣지 않는다 (카페 → 카페 방지)
 * ③ 한 곳씩 넣으면서 어림한 총 소요시간이 이용 시간 안이면 계속, 들어갈 장소가 없으면 멈춘다
 * ④ 방문 순서는 출발지에서 가장 가까운 곳 → 그다음 가까운 곳
 * ⑤ 실제 이동시간으로 이용 시간을 넘으면 마지막 장소를 빼고 다시 확인, 다 빠지면 그 코스는 버린다
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

    private final PlaceCountCalculator placeCountCalculator;
    private final StayTimeCalculator stayTimeCalculator;
    private final StraightLineTravelCalculator straightLineTravelCalculator;
    private final CourseTimeCalculator courseTimeCalculator;
    private final TravelTimeService travelTimeService;

    /** 후보를 섞는 데 쓴다. 테스트에서는 씨앗(seed)을 고정한 Random 을 넣어 결과를 매번 같게 만든다 */
    private final Random random;

    /**
     * 스프링이 쓰는 생성자. 생성자가 두 개라서 어느 것을 쓸지 @Autowired 로 알려준다.
     */
    @Autowired
    public CourseComposer(PlaceCountCalculator placeCountCalculator,
                          StayTimeCalculator stayTimeCalculator,
                          StraightLineTravelCalculator straightLineTravelCalculator,
                          CourseTimeCalculator courseTimeCalculator,
                          TravelTimeService travelTimeService) {
        this(placeCountCalculator, stayTimeCalculator, straightLineTravelCalculator,
                courseTimeCalculator, travelTimeService, new Random());
    }

    /** 테스트용 생성자 (package-private). 섞는 순서를 고정하려고 Random 을 밖에서 받는다 */
    CourseComposer(PlaceCountCalculator placeCountCalculator,
                   StayTimeCalculator stayTimeCalculator,
                   StraightLineTravelCalculator straightLineTravelCalculator,
                   CourseTimeCalculator courseTimeCalculator,
                   TravelTimeService travelTimeService,
                   Random random) {
        this.placeCountCalculator = placeCountCalculator;
        this.stayTimeCalculator = stayTimeCalculator;
        this.straightLineTravelCalculator = straightLineTravelCalculator;
        this.courseTimeCalculator = courseTimeCalculator;
        this.travelTimeService = travelTimeService;
        this.random = random;
    }

    /**
     * 서로 다른 코스를 최대 3개 만든다.
     *
     * @param candidates 후보 장소 (장소 도메인에서 받은 것)
     * @return 완성된 코스 0 ~ 3개. 만들 수 있는 코스가 없으면 빈 목록 (오류 아님, REQ-027)
     */
    public List<CoursePlan> compose(CourseCondition condition, List<PlaceCandidate> candidates) {
        List<CoursePlan> courses = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return courses;
        }

        int maxPlaceCount = placeCountCalculator.calculateMaxPlaceCount(condition.availableMinutes());
        Set<Set<String>> triedPlaceSets = new HashSet<>(); // 이미 만들어 본 장소 묶음 (API 를 두 번 부르지 않게)

        for (int attempt = 0; attempt < MAX_ATTEMPTS && courses.size() < MAX_COURSE_COUNT; attempt++) {
            // 1단계: 후보를 섞고 고르기 (API 호출 없음)
            List<PlaceCandidate> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, random);
            List<PlaceCandidate> picked = pickPlaces(condition, shuffled, maxPlaceCount, attempt);

            if (picked.isEmpty() || !triedPlaceSets.add(placeIdsOf(picked))) {
                continue; // 고를 게 없거나, 이미 해 본 묶음이면 건너뜀 (Set.add 는 이미 있으면 false)
            }

            // 2단계: 고른 코스만 실제 이동시간으로 확정
            confirmCourse(condition, picked)
                    .filter(course -> isNewCourse(courses, course)) // 마지막 장소를 빼다가 기존 코스와 같아질 수 있음
                    .ifPresent(courses::add);
        }
        return courses;
    }

    // ===================== 1단계: 고르기 (직선거리 어림) =====================

    /**
     * 장소 수 상한 안에서 한 곳씩 고른다. 반환 목록은 이미 방문 순서대로 정렬되어 있다.
     *
     * @param shuffled 섞인 후보. 앞에서부터 보면서 조건에 맞는 첫 장소를 고른다
     * @param attempt  몇 번째 시도인지. 직접 선택일 때 시작 대분류를 바꾸는 데 쓴다 (규칙 ①)
     */
    List<PlaceCandidate> pickPlaces(CourseCondition condition, List<PlaceCandidate> shuffled,
                                    int maxPlaceCount, int attempt) {
        List<PlaceCandidate> picked = new ArrayList<>();

        for (int slot = 0; slot < maxPlaceCount; slot++) {
            Optional<PlaceCandidate> next = findPlaceThatFits(condition, shuffled, picked, slot + attempt);
            if (next.isEmpty()) {
                break; // 더 넣을 수 있는 장소가 없음 → 지금까지 고른 만큼만 (규칙 ③)
            }
            picked.add(next.get());
            picked = orderByNearest(condition.origin(), picked); // 넣을 때마다 방문 순서 다시 정하기 (규칙 ④)
        }
        return picked;
    }

    /**
     * 이번 자리에 넣을 장소 하나를 찾는다.
     * 원하는 대분류부터 찾고, 없으면 나머지 고른 대분류에서 찾는다 (그 대분류 후보가 바닥났을 때).
     */
    private Optional<PlaceCandidate> findPlaceThatFits(CourseCondition condition, List<PlaceCandidate> shuffled,
                                                       List<PlaceCandidate> picked, int turn) {
        for (CategoryCode category : categoryOrder(condition, turn)) {
            for (PlaceCandidate candidate : shuffled) {
                if (canAdd(candidate, category, picked)
                        && estimateTotalMinutes(condition, withPlace(picked, candidate)) <= condition.availableMinutes()) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 이번 차례에 찾아볼 대분류 순서 (규칙 ①).
     * 예) 고른 대분류 [카페, 음식], turn 0 → [카페, 음식] / turn 1 → [음식, 카페] / turn 2 → [카페, 음식]
     * 랜덤이면 대분류를 따지지 않으므로 null 하나만 담는다 (null = 아무 대분류나).
     */
    private List<CategoryCode> categoryOrder(CourseCondition condition, int turn) {
        if (condition.isRandom()) {
            return Collections.singletonList(null); // List.of(null) 은 null 을 못 담아서 이걸 쓴다
        }
        List<CategoryCode> categories = condition.categories();
        List<CategoryCode> order = new ArrayList<>();
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
     */
    private boolean canAdd(PlaceCandidate candidate, CategoryCode category, List<PlaceCandidate> picked) {
        if (category != null && candidate.category() != category) {
            return false;
        }
        for (PlaceCandidate place : picked) {
            if (place.placeId().equals(candidate.placeId())
                    || Objects.equals(place.subCategory(), candidate.subCategory())) { // null 이어도 안전하게 비교
                return false;
            }
        }
        return true;
    }

    /**
     * 직선거리로 어림한 총 소요시간 = 체류 합 + 이동 합 (+ 도착지가 있으면 마지막 → 도착지).
     * 넣을지 말지 판단만 하는 값이라 API 를 부르지 않는다.
     */
    int estimateTotalMinutes(CourseCondition condition, List<PlaceCandidate> places) {
        List<PlaceCandidate> ordered = orderByNearest(condition.origin(), places);
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

            CoursePlan course = toCoursePlan(kept, finalTravel);
            if (courseTimeCalculator.isWithinAvailableTime(course.totalMinutes(), condition.availableMinutes())) {
                return Optional.of(course);
            }
        }
        return Optional.empty(); // 한 곳만 남겨도 넘음 → 이 코스는 버린다
    }

    /** 방문지 목록 → 총 소요시간·이동 합·추정 여부를 계산해 CoursePlan 으로 만든다 */
    private CoursePlan toCoursePlan(List<CourseStop> stops, TravelRoute finalTravel) {
        List<PlaceVisit> visits = new ArrayList<>();
        int travelSum = 0;
        boolean estimated = false;

        for (CourseStop stop : stops) {
            visits.add(new PlaceVisit(stop.travel().travelMinutes(), stop.stayMinutes()));
            travelSum += stop.travel().travelMinutes();
            estimated = estimated || stop.travel().estimated(); // 하나라도 추정이면 true
        }

        int finalTravelMinutes = 0;
        if (finalTravel != null) {
            finalTravelMinutes = finalTravel.travelMinutes();
            estimated = estimated || finalTravel.estimated();
        }

        int totalMinutes = courseTimeCalculator.calculateTotalMinutes(visits, finalTravelMinutes);
        return new CoursePlan(stops, finalTravel, totalMinutes, travelSum + finalTravelMinutes, estimated);
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
