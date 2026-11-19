package com.teumteum.recommend.service;

import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceAlternative;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import com.teumteum.recommend.dto.AlternativePlaceListResponse;
import com.teumteum.recommend.dto.PlaceReplaceRequest;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.exception.RecommendErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 장소 교체 (DET-03, 명세 4·5장). 후보 조회(6-1)와 교체 확정(6-2).
 * <p>
 * RecommendService 에 넣지 않고 따로 둔 이유: 추천 생성·다시 추천·상세만으로도 RecommendService 가 길다.
 * 교체는 "코스 하나 안의 장소 하나" 를 다루는 별도 기능이라 나눠 두면 읽기도, 테스트하기도 쉽다.
 * 코스를 꺼내는 규칙(404·410)과 새 ID 로 보관하는 일은 RecommendService 의 것을 그대로 써서 두 곳이 어긋나지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceReplaceService {

    private final RecommendService recommendService;
    private final AlternativeFinder alternativeFinder;
    private final RecommendResponseMapper responseMapper;
    private final TravelTimeService travelTimeService;
    private final StayTimeCalculator stayTimeCalculator;
    private final CourseTimeCalculator courseTimeCalculator;

    /**
     * 교체 후보 조회 (GET /api/recommendations/courses/{recommendationId}/places/{placeId}/alternatives).
     *
     * @return 바꾸려는 장소 요약 + 후보 0 ~ 5곳. 후보가 없으면 alternatives []
     * @throws CustomException 없는 코스 또는 코스에 없는 장소 → 404, 만료 → 410
     */
    public AlternativePlaceListResponse getAlternatives(String recommendationId, String placeId) {
        RecommendedCourse course = recommendService.findValidCourse(recommendationId);
        RecommendationSet recommendationSet = recommendService.findSetOf(course);
        int targetIndex = indexOf(course.plan(), placeId);

        List<PlaceAlternative> alternatives = alternativeFinder.find(
                recommendationSet.condition().toCourseCondition(), course.plan(), targetIndex,
                recommendationSet.candidates());

        return responseMapper.toAlternativeListResponse(course, targetIndex, alternatives);
    }

    /**
     * 장소 교체 (POST /api/recommendations/courses/{recommendationId}/replace, 명세 5장, REQ-029).
     * <pre>
     * 1. 코스 꺼내기 (없으면 404, 만료면 410), 바꿀 장소가 코스에 없으면 404
     * 2. 새 장소가 교체 후보 5곳 안에 있는지 확인 (없으면 400) ← 후보 조회와 같은 규칙으로 다시 계산
     * 3. 바뀐 앞뒤 구간만 이동시간 API 로 다시 계산 (최대 2번 호출, 실패하면 직선 추정)
     * 4. 총 시간이 이용 시간을 넘으면 409 (아무것도 보관하지 않음 → 코스는 그대로)
     * 5. 새 recommendationId 로 보관 (원래 코스도 남김) → 코스 상세 형식으로 응답
     * </pre>
     * 후보 목록의 시간은 직선 추정이라, 목록에서 회색이 아니었어도 실제 계산으로는 넘을 수 있다.
     * 그래서 서버가 4번에서 다시 확인한다 (명세 5장 "서버는 요청을 받을 때 다시 확인한다").
     */
    public RecommendCourseDetailResponse replace(String recommendationId, PlaceReplaceRequest request) {
        RecommendedCourse course = recommendService.findValidCourse(recommendationId);
        RecommendationSet recommendationSet = recommendService.findSetOf(course);
        CourseCondition condition = recommendationSet.condition().toCourseCondition();
        int targetIndex = indexOf(course.plan(), request.targetPlaceId());

        PlaceCandidate newPlace = findAmongAlternatives(
                condition, course.plan(), targetIndex, recommendationSet.candidates(), request.newPlaceId());

        CoursePlan replacedPlan = replacePlace(condition, course.plan(), targetIndex, newPlace);
        if (!courseTimeCalculator.isWithinAvailableTime(replacedPlan.totalMinutes(), condition.availableMinutes())) {
            throw new CustomException(RecommendErrorCode.AVAILABLE_TIME_EXCEEDED);
        }

        RecommendedCourse replaced = recommendService.saveReplacedCourse(course, replacedPlan);
        return responseMapper.toCourseDetailResponse(replaced, recommendationSet.condition());
    }

    /** 코스 안에서 장소의 위치(0부터). 코스에 없는 장소면 404 (명세 4·5장) */
    int indexOf(CoursePlan plan, String placeId) {
        for (int i = 0; i < plan.stops().size(); i++) {
            if (plan.stops().get(i).place().placeId().equals(placeId)) {
                return i;
            }
        }
        throw new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND);
    }

    /**
     * 새 장소가 교체 후보(최대 5곳) 안에 있으면 그 장소를 돌려준다. 없으면 400 (명세 5장 "후보 목록에 없음").
     * 후보 조회 API 와 같은 AlternativeFinder 로 다시 계산하므로, 화면에 보였던 후보만 통과한다.
     */
    private PlaceCandidate findAmongAlternatives(CourseCondition condition, CoursePlan plan, int targetIndex,
                                                 List<PlaceCandidate> candidates, String newPlaceId) {
        return alternativeFinder.find(condition, plan, targetIndex, candidates).stream()
                .map(PlaceAlternative::place)
                .filter(place -> place.placeId().equals(newPlaceId))
                .findFirst()
                .orElseThrow(() -> new CustomException(CommonErrorCode.INVALID_REQUEST));
    }

    /**
     * 한 곳만 바꾼 새 코스를 만든다. 바뀌는 구간만 이동시간 API 를 부른다 (NFR-014).
     * <pre>
     * 예) 출발지 → A → [B] → C → 도착지 에서 B 를 X 로 바꾸면
     *     A → X   : 새로 계산
     *     X → C   : 새로 계산 (C 의 travel 이 바뀜)
     *     나머지  : 그대로 (출발지 → A, C → 도착지)
     * 마지막 장소를 바꾸면 "X → 도착지" 를 새로 계산한다 (도착지 없으면 계산 안 함).
     * </pre>
     * 원래 코스의 목록은 고치지 않고 복사본을 고친다 (원래 코스는 그대로 남아야 한다).
     */
    CoursePlan replacePlace(CourseCondition condition, CoursePlan plan, int targetIndex, PlaceCandidate newPlace) {
        List<CourseStop> stops = new ArrayList<>(plan.stops()); // 복사본
        Coordinate newPoint = coordinateOf(newPlace);

        // 앞 지점 → 새 장소
        Coordinate previous = (targetIndex == 0) ? condition.origin() : coordinateOf(stops.get(targetIndex - 1).place());
        TravelRoute travelIn = findRoute(condition, previous, newPoint);
        // 원래 장소에 쓰던 체류시간을 새 장소의 최소~최대 안에서 이어받는다 (#204, 교체 후보 계산과 같은 규칙)
        int stayMinutes = stayTimeCalculator.fitWithin(newPlace.subCategory(), stops.get(targetIndex).stayMinutes());
        stops.set(targetIndex, new CourseStop(newPlace, stayMinutes, travelIn));

        // 새 장소 → 뒤 지점
        TravelRoute finalTravel = plan.finalTravel();
        boolean isLast = targetIndex == stops.size() - 1;
        if (!isLast) {
            CourseStop next = stops.get(targetIndex + 1);
            TravelRoute travelOut = findRoute(condition, newPoint, coordinateOf(next.place()));
            stops.set(targetIndex + 1, new CourseStop(next.place(), next.stayMinutes(), travelOut));
        } else if (condition.hasDestination()) {
            finalTravel = findRoute(condition, newPoint, condition.destination());
        }

        return courseTimeCalculator.toCoursePlan(stops, finalTravel);
    }

    private TravelRoute findRoute(CourseCondition condition, Coordinate from, Coordinate to) {
        return travelTimeService.findRoute(condition.transportType(),
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private Coordinate coordinateOf(PlaceCandidate place) {
        return new Coordinate(place.latitude(), place.longitude());
    }
}
