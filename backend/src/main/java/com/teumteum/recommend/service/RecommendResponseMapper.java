package com.teumteum.recommend.service;

import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PathType;
import com.teumteum.recommend.domain.PlaceAlternative;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.RecommendCondition;
import com.teumteum.recommend.domain.RecommendCondition.Location;
import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import com.teumteum.global.common.category.SubCategory;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import com.teumteum.recommend.dto.AlternativePlaceListResponse;
import com.teumteum.recommend.dto.AlternativePlaceResponse;
import com.teumteum.recommend.dto.CoordinateResponse;
import com.teumteum.recommend.dto.CurrentPlaceResponse;
import com.teumteum.recommend.dto.LocationResponse;
import com.teumteum.recommend.dto.RecommendConditionResponse;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.dto.RecommendCourseResponse;
import com.teumteum.recommend.dto.RecommendPlaceDetailResponse;
import com.teumteum.recommend.dto.RecommendPlaceResponse;
import com.teumteum.recommend.dto.RecommendResponse;
import com.teumteum.recommend.dto.RouteLegResponse;
import com.teumteum.recommend.dto.SubCategoryFilterResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 보관한 추천 결과(도메인) → 응답 DTO 변환 (recommend-api.md 1.3).
 * <p>
 * 서비스에서 분리한 이유: 변환 코드가 길고, 5-2 의 다시 추천도 같은 응답(RecommendResponse)을 쓴다.
 * 엔티티·도메인을 컨트롤러 응답으로 그대로 내보내지 않는다는 팀 규칙(AGENTS.md 5장)을 여기서 지킨다.
 */
@Component
public class RecommendResponseMapper {

    /** 코스명에서 장소 이름 사이에 넣는 글자 (예: "당구장 A → 소품샵 B") */
    static final String COURSE_NAME_SEPARATOR = " → ";

    public RecommendResponse toResponse(RecommendationSet recommendationSet) {
        RecommendCondition condition = recommendationSet.condition();

        List<RecommendCourseResponse> courses = new ArrayList<>();
        for (RecommendedCourse course : recommendationSet.courses()) {
            courses.add(toCourseResponse(course, condition.startTime()));
        }

        return new RecommendResponse(
                recommendationSet.recommendationSetId(),
                recommendationSet.expiresAt(),
                toConditionResponse(condition),
                toSubCategoryFilters(condition, recommendationSet.courses()),
                courses);
    }

    // ===================== 조건 =====================

    RecommendConditionResponse toConditionResponse(RecommendCondition condition) {
        return new RecommendConditionResponse(
                toLocationResponse(condition.origin()),
                condition.destination() == null ? null : toLocationResponse(condition.destination()),
                condition.startTime(),
                condition.availableMinutes(),
                condition.transportType(),
                condition.categoryMode(),   // DIRECT 인데 비어서 랜덤 처리했다면 RANDOM 으로 나간다 (명세 1.3)
                condition.categories());
    }

    private LocationResponse toLocationResponse(Location location) {
        return new LocationResponse(location.name(), location.address(), location.latitude(), location.longitude());
    }

    // ===================== 코스 · 장소 =====================

    RecommendCourseResponse toCourseResponse(RecommendedCourse course, String startTime) {
        List<CourseStop> stops = course.plan().stops();

        List<RecommendPlaceResponse> places = new ArrayList<>();
        List<String> placeNames = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            places.add(toPlaceResponse(stops.get(i), i + 1)); // 방문 순서는 1부터
            placeNames.add(stops.get(i).place().placeName());
        }

        return new RecommendCourseResponse(
                course.recommendationId(),
                String.join(COURSE_NAME_SEPARATOR, placeNames), // 코스명 = 장소 이름을 → 로 잇기
                stops.size(),
                startTime,
                course.plan().totalMinutes(),
                course.plan().totalTravelMinutes(),
                course.plan().finalTravelMinutes(),
                course.plan().estimated(),
                places);
    }

    private RecommendPlaceResponse toPlaceResponse(CourseStop stop, int visitOrder) {
        return new RecommendPlaceResponse(
                stop.place().placeId(),
                visitOrder,
                stop.place().placeName(),
                stop.place().category(),
                stop.place().subCategory(),
                stop.place().address(),
                stop.place().latitude(),
                stop.place().longitude(),
                null,                           // imageUrl: 장소 쪽 사진 데이터가 생기면 연결 (지금은 프론트 기본 이미지)
                null,                           // openingHours: 운영시간 데이터가 생기면 연결 (REQ-018, P1)
                stop.stayMinutes(),
                stop.travel().travelMinutes(),  // 직전 지점 → 이 장소
                stop.travel().transitInfo());   // 대중교통이 아니면 null
    }

    // ===================== 코스 상세 (명세 3장) =====================

    /**
     * 보관한 코스 → 코스 상세 응답 (DET-01 일정, DET-02 지도).
     * 출발지·도착지·이동수단·이용 시간은 코스가 아니라 조건에 있어서 묶음의 조건을 함께 받는다.
     */
    public RecommendCourseDetailResponse toCourseDetailResponse(RecommendedCourse course,
                                                                RecommendCondition condition) {
        CoursePlan plan = course.plan();
        List<CourseStop> stops = plan.stops();

        List<RecommendPlaceDetailResponse> places = new ArrayList<>();
        List<String> placeNames = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            places.add(toPlaceDetailResponse(stops.get(i), i + 1));
            placeNames.add(stops.get(i).place().placeName());
        }

        return new RecommendCourseDetailResponse(
                course.recommendationId(),
                course.recommendationSetId(),
                course.expiresAt(),
                toLocationResponse(condition.origin()),
                condition.destination() == null ? null : toLocationResponse(condition.destination()),
                condition.transportType(),
                condition.availableMinutes(),
                String.join(COURSE_NAME_SEPARATOR, placeNames),
                stops.size(),
                condition.startTime(),
                plan.totalMinutes(),
                plan.totalTravelMinutes(),
                plan.finalTravelMinutes(),
                plan.estimated(),
                places,
                toRouteLegs(condition, plan));
    }

    private RecommendPlaceDetailResponse toPlaceDetailResponse(CourseStop stop, int visitOrder) {
        PlaceCandidate place = stop.place();
        return new RecommendPlaceDetailResponse(
                place.placeId(),
                visitOrder,
                place.placeName(),
                place.category(),
                place.subCategory(),
                place.address(),
                place.latitude(),
                place.longitude(),
                null,                           // imageUrl: 아직 데이터 없음
                null,                           // openingHours: 아직 데이터 없음 (REQ-018, P1)
                stop.stayMinutes(),
                stop.travel().travelMinutes(),
                stop.travel().transitInfo(),
                null,                           // description: 장소 소개 데이터(TourAPI)가 생기면 연결
                place.placeUrl());              // 카카오맵에서 보기
    }

    /**
     * 지도 경로 구간들을 만든다. 번호는 출발지 0, 장소 1 ~ N, 도착지 N + 1.
     * <pre>
     * 출발지(0) → 장소1(1) → 장소2(2) → [도착지(3), 있을 때만]
     * </pre>
     * 이동시간 API 를 다시 부르지 않고, 추천할 때 받아 둔 경로 좌표(TravelRoute.coordinates)를 쓴다 (명세 3장).
     */
    List<RouteLegResponse> toRouteLegs(RecommendCondition condition, CoursePlan plan) {
        List<RouteLegResponse> legs = new ArrayList<>();
        List<CourseStop> stops = plan.stops();

        Coordinate previous = condition.origin().toCoordinate(); // 직전 지점 (처음엔 출발지)
        for (int i = 0; i < stops.size(); i++) {
            PlaceCandidate place = stops.get(i).place();
            Coordinate current = new Coordinate(place.latitude(), place.longitude());
            legs.add(toRouteLeg(i, i + 1, stops.get(i).travel(), previous, current));
            previous = current;
        }

        // 마지막 장소 → 도착지 (도착지가 없으면 이 구간도 없다)
        if (plan.finalTravel() != null && condition.destination() != null) {
            legs.add(toRouteLeg(stops.size(), stops.size() + 1, plan.finalTravel(),
                    previous, condition.destination().toCoordinate()));
        }
        return legs;
    }

    /**
     * 한 구간.
     * - 경로 좌표가 있으면 (차량·도보 API 성공) → ACTUAL, 그 좌표 그대로
     * - 없으면 (대중교통 1차 버전, 직선거리 추정) → STRAIGHT, 앞 지점과 뒤 지점 두 점
     */
    private RouteLegResponse toRouteLeg(int fromVisitOrder, int toVisitOrder, TravelRoute travel,
                                        Coordinate start, Coordinate end) {
        List<Coordinate> path = travel.coordinates();
        if (path.size() >= 2) {
            return new RouteLegResponse(fromVisitOrder, toVisitOrder, PathType.ACTUAL, toCoordinateResponses(path));
        }
        return new RouteLegResponse(fromVisitOrder, toVisitOrder, PathType.STRAIGHT,
                toCoordinateResponses(List.of(start, end)));
    }

    private List<CoordinateResponse> toCoordinateResponses(List<Coordinate> coordinates) {
        return coordinates.stream()
                .map(c -> new CoordinateResponse(c.latitude(), c.longitude()))
                .toList();
    }

    // ===================== 장소 교체 후보 =====================

    /**
     * 교체 후보 응답 (명세 4장). 위쪽 "지금: 카페 A" + 후보 목록.
     *
     * @param targetIndex 바꿀 장소의 위치 (0부터). 응답의 visitOrder 는 1부터라 + 1
     */
    public AlternativePlaceListResponse toAlternativeListResponse(RecommendedCourse course, int targetIndex,
                                                                  List<PlaceAlternative> alternatives) {
        CourseStop target = course.plan().stops().get(targetIndex);
        PlaceCandidate place = target.place();
        CurrentPlaceResponse currentPlace = new CurrentPlaceResponse(
                place.placeId(), targetIndex + 1, place.placeName(),
                place.category(), place.subCategory(), target.stayMinutes());

        List<AlternativePlaceResponse> responses = alternatives.stream()
                .map(this::toAlternativeResponse)
                .toList();
        return new AlternativePlaceListResponse(course.recommendationId(), currentPlace, responses);
    }

    private AlternativePlaceResponse toAlternativeResponse(PlaceAlternative alternative) {
        PlaceCandidate place = alternative.place();
        return new AlternativePlaceResponse(
                place.placeId(),
                place.placeName(),
                place.category(),
                place.subCategory(),
                place.address(),
                place.latitude(),
                place.longitude(),
                null,                               // imageUrl: 아직 데이터 없음
                alternative.stayMinutes(),
                alternative.travelMinutes(),
                null,                               // transitInfo: 직선 추정이라 노선 정보 없음
                alternative.diffMinutes(),
                alternative.exceedsAvailableTime(),
                true);                              // estimated: 후보 목록은 항상 직선 추정 (AlternativeFinder)
    }

    // ===================== 세분류 필터 =====================

    /**
     * 세분류 필터를 대분류별로 묶는다 (REQ-050).
     * - 직접 선택: 고른 대분류 순서대로, 그 대분류의 세분류 전체
     * - 랜덤: 결과 코스에 실제로 나온 세분류만, 대분류 표 순서대로 (고른 대분류가 없으니 결과 기준)
     */
    List<SubCategoryFilterResponse> toSubCategoryFilters(RecommendCondition condition,
                                                         List<RecommendedCourse> courses) {
        List<SubCategoryFilterResponse> filters = new ArrayList<>();

        if (!condition.categories().isEmpty()) {
            for (Category category : condition.categories()) {
                filters.add(filterOf(category, SubCategory.of(category)));
            }
            return filters;
        }

        Set<SubCategory> appeared = subCategoriesIn(courses);
        for (Category category : Category.values()) {
            List<SubCategory> subs = new ArrayList<>(SubCategory.of(category));
            subs.retainAll(appeared); // 결과에 나온 것만 남긴다 (retainAll = 교집합)
            if (!subs.isEmpty()) {
                filters.add(filterOf(category, subs));
            }
        }
        return filters;
    }

    /** 결과 코스들에 나온 세분류 (세분류 표에 없는 이름은 필터에 넣지 않는다) */
    private Set<SubCategory> subCategoriesIn(List<RecommendedCourse> courses) {
        Set<SubCategory> result = EnumSet.noneOf(SubCategory.class);
        for (RecommendedCourse course : courses) {
            for (CourseStop stop : course.plan().stops()) {
                SubCategory.fromDisplayName(stop.place().subCategory()).ifPresent(result::add);
            }
        }
        return result;
    }

    private SubCategoryFilterResponse filterOf(Category category, List<SubCategory> subs) {
        List<String> names = subs.stream().map(SubCategory::getDisplayName).toList();
        return new SubCategoryFilterResponse(category, category.getDisplayName(), names);
    }
}
