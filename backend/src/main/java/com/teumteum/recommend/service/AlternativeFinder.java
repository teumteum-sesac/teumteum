package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceAlternative;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 장소 교체 후보 고르기 + "바꾸면 총 시간이 얼마나 달라지는지" 계산 (명세 4장, REQ-029).
 * <pre>
 * 예) 출발지 ──15분──▶ [카페 A] ──10분──▶ 공원 B          (카페 A 를 바꾸려 함)
 *               앞 지점 = 출발지, 뒤 지점 = 공원 B
 *
 * 1. 고르기: 코스에 없는 장소 중
 *    ① 같은 세분류(카페) 를 앞·뒤 지점과 가까운 순으로
 *    ② 5곳이 안 되면 같은 대분류(카페/디저트)의 다른 세분류(베이커리·디저트) 로 채움
 * 2. 계산 (직선거리 추정, 이동시간 API 호출 없음):
 *    바뀌는 부분 = 앞 지점 → 장소 이동 + 체류 + 장소 → 뒤 지점 이동
 *    diffMinutes = 후보로 바꾼 값 − 지금 값
 *    바꾼 뒤 총 시간이 이용 시간을 넘으면 exceedsAvailableTime true
 * </pre>
 * 6-2 교체 확정도 "보낸 newPlaceId 가 이 후보 안에 있는지" 를 같은 메서드로 확인한다 (규칙을 한 곳에 둔다).
 */
@Component
@RequiredArgsConstructor
public class AlternativeFinder {

    /** 보여 줄 교체 후보 수 (명세 4장 "5곳 정도") */
    static final int MAX_ALTERNATIVES = 5;

    private final StayTimeCalculator stayTimeCalculator;
    private final StraightLineTravelCalculator straightLineTravelCalculator;
    private final CourseTimeCalculator courseTimeCalculator;

    /**
     * @param condition   코스 조건 (출발지·도착지·이동수단·이용 시간)
     * @param plan        지금 코스
     * @param targetIndex 바꿀 장소의 위치 (0부터, plan.stops() 안의 순서)
     * @param candidates  추천할 때 보관한 장소 후보
     * @return 교체 후보 0 ~ 5곳 (같은 세분류 먼저, 그 안에서 가까운 순). 없으면 빈 목록
     */
    public List<PlaceAlternative> find(CourseCondition condition, CoursePlan plan, int targetIndex,
                                       List<PlaceCandidate> candidates) {
        PlaceCandidate target = plan.stops().get(targetIndex).place();
        Coordinate previous = previousPoint(condition, plan, targetIndex);
        Coordinate next = nextPoint(condition, plan, targetIndex); // 마지막 장소이고 도착지가 없으면 null
        Set<String> placeIdsInCourse = plan.placeIds();

        // 앞 지점까지 거리 + 뒤 지점까지 거리가 작은 순 = 코스 흐름에서 덜 돌아가는 순
        Comparator<PlaceCandidate> nearestFirst =
                Comparator.comparingDouble(candidate -> detourDistanceKm(previous, candidate, next));

        List<PlaceCandidate> available = candidates.stream()
                .filter(candidate -> !placeIdsInCourse.contains(candidate.placeId())) // 코스에 이미 있는 장소 제외
                .filter(candidate -> candidate.category() == target.category())       // 같은 대분류만
                .toList();

        Stream<PlaceCandidate> sameSubCategory = available.stream()
                .filter(candidate -> Objects.equals(candidate.subCategory(), target.subCategory()))
                .sorted(nearestFirst);
        Stream<PlaceCandidate> otherSubCategory = available.stream()
                .filter(candidate -> !Objects.equals(candidate.subCategory(), target.subCategory()))
                .sorted(nearestFirst);

        // 같은 세분류를 앞에 두고 이어 붙인 뒤 5곳까지 자른다 → 같은 세분류가 5곳 이상이면 다른 세분류는 안 나온다
        return Stream.concat(sameSubCategory, otherSubCategory)
                .limit(MAX_ALTERNATIVES)
                .map(candidate -> toAlternative(condition, plan, targetIndex, previous, next, candidate))
                .toList();
    }

    /** 후보 하나로 바꿨을 때의 체류·이동·총 시간 변화 계산 */
    private PlaceAlternative toAlternative(CourseCondition condition, CoursePlan plan, int targetIndex,
                                           Coordinate previous, Coordinate next, PlaceCandidate candidate) {
        CourseStop current = plan.stops().get(targetIndex);
        int currentMinutes = current.travel().travelMinutes() + current.stayMinutes()
                + travelOutMinutes(plan, targetIndex);

        int stayMinutes = stayTimeCalculator.calculate(candidate.subCategory());
        int travelInMinutes = estimateMinutes(condition, previous, toCoordinate(candidate));
        int travelOutMinutes = (next == null) ? 0 : estimateMinutes(condition, toCoordinate(candidate), next);
        int newMinutes = travelInMinutes + stayMinutes + travelOutMinutes;

        int diffMinutes = newMinutes - currentMinutes;
        boolean exceeds = !courseTimeCalculator.isWithinAvailableTime(
                plan.totalMinutes() + diffMinutes, condition.availableMinutes());

        return new PlaceAlternative(candidate, stayMinutes, travelInMinutes, diffMinutes, exceeds);
    }

    /** 바꿀 장소 → 뒤 지점 이동시간(지금 코스 값). 뒤가 다음 장소면 그 장소의 travel, 마지막이면 도착지까지(없으면 0) */
    private int travelOutMinutes(CoursePlan plan, int targetIndex) {
        boolean isLast = targetIndex == plan.stops().size() - 1;
        return isLast ? plan.finalTravelMinutes() : plan.stops().get(targetIndex + 1).travel().travelMinutes();
    }

    /** 앞 지점: 첫 장소면 출발지, 아니면 직전 장소 */
    private Coordinate previousPoint(CourseCondition condition, CoursePlan plan, int targetIndex) {
        return (targetIndex == 0) ? condition.origin() : toCoordinate(plan.stops().get(targetIndex - 1).place());
    }

    /** 뒤 지점: 마지막 장소가 아니면 다음 장소, 마지막이면 도착지 (없으면 null) */
    private Coordinate nextPoint(CourseCondition condition, CoursePlan plan, int targetIndex) {
        boolean isLast = targetIndex == plan.stops().size() - 1;
        return isLast ? condition.destination() : toCoordinate(plan.stops().get(targetIndex + 1).place());
    }

    /** 앞 지점 → 후보 → 뒤 지점 직선거리 합(km). 뒤 지점이 없으면 앞 지점 거리만 */
    private double detourDistanceKm(Coordinate previous, PlaceCandidate candidate, Coordinate next) {
        double distance = distanceKm(previous, toCoordinate(candidate));
        if (next != null) {
            distance += distanceKm(toCoordinate(candidate), next);
        }
        return distance;
    }

    private double distanceKm(Coordinate from, Coordinate to) {
        return straightLineTravelCalculator.calculateDistanceKm(
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private int estimateMinutes(CourseCondition condition, Coordinate from, Coordinate to) {
        return straightLineTravelCalculator.calculateTravelMinutes(condition.transportType(),
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private Coordinate toCoordinate(PlaceCandidate place) {
        return new Coordinate(place.latitude(), place.longitude());
    }
}
