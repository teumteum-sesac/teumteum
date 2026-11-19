package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.common.category.Category;
import com.teumteum.place.dto.NearbyPlaceResult;
import com.teumteum.place.service.NearbyPlaceService;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseSearchResult;
import com.teumteum.recommend.domain.PlaceCandidate;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 주변 장소 후보를 받아 코스를 찾는다. 코스가 3개가 안 되면 반경을 넓혀 다시 찾는다 (REQ-015, REQ-027).
 * <p>
 * 흐름 (명세 1.6 서버 처리 순서 3 ~ 6)
 * <pre>
 * 반경 1km → 후보 검색(장소 도메인) → 코스 조합(CourseComposer) → 3개 모이면 끝
 *            (출발지 + 먼 지점 2곳, 도착지가 멀면 출발지·1/3·2/3·도착지 4곳 주변에서 찾아 합친다)
 *          ↓ 모자라면
 * 반경 2km → 3km → 5km 차례로, 모자란 만큼만 더 찾는다 (이동수단 최대 반경까지만. 도보는 2km)
 * 끝까지 넓혀도 없으면 빈 목록 (오류 아님 → 프론트가 "조건에 맞는 코스가 없어요" 안내)
 * </pre>
 * 장소 도메인은 NearbyPlaceService(서비스)를 통해서만 부른다 (도메인 경계 규칙).
 * 장소 쪽 결과(NearbyPlaceResult)는 여기서 추천 쪽 모양(PlaceCandidate)으로 바꾼다.
 * 그래서 장소 쪽 응답이 바뀌어도 이 클래스의 변환 부분만 고치면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSearchService {

    /**
     * 차례로 넓혀 쓸 검색 반경(m): 1km → 2km → 3km → 5km.
     * 장소 담당(정종일)이 정한 단계를 그대로 쓴다 (나혜빈 확정, 2026-11-17).
     */
    static final List<Integer> RADIUS_STEPS = NearbyPlaceService.RADIUS_STEPS;

    /** 위도 1도의 거리(km). 지구 어디서나 거의 같다 */
    static final double KM_PER_LATITUDE_DEGREE = 111.0;

    private final NearbyPlaceService nearbyPlaceService;
    private final CourseComposer courseComposer;

    /**
     * 먼 지점의 방향을 고르는 무작위 값. 스프링이 넣어 주는 값이 아니라 여기서 바로 만든다.
     * (@RequiredArgsConstructor 는 값을 이미 넣어 둔 final 필드는 생성자에 넣지 않는다)
     */
    private final Random random = new Random();

    /**
     * 조건에 맞는 코스를 최대 3개 찾는다.
     *
     * @return 코스 0 ~ 3개(반경을 끝까지 넓혀도 없으면 빈 목록) + 그동안 받은 장소 후보 (교체 후보용으로 보관)
     * @throws CustomException 장소 검색 외부 API 가 전부 실패하고, 그때까지 찾은 코스도 없을 때 (장소 쪽 502·503 그대로 전달)
     */
    public CourseSearchResult findCourses(CourseCondition condition) {
        return findCourses(condition, Set.of());
    }

    /**
     * 정해진 장소를 빼고 코스를 최대 3개 찾는다 (다시 추천, 명세 2장).
     * 후보 목록에서 제외할 장소를 먼저 걸러 낸 뒤 코스를 조합한다. 나머지 흐름은 위와 같다.
     * <p>
     * 돌려주는 후보(candidates)에는 제외한 장소도 들어 있다. 제외는 "새 코스를 만들 때" 만 적용하고,
     * 교체 후보로는 직전 추천에 나왔던 장소도 보여 줄 수 있게 한다.
     *
     * @param excludedPlaceIds 후보에서 뺄 장소 ID (직전 추천에 나온 장소). 비어 있으면 아무것도 안 뺀다
     */
    public CourseSearchResult findCourses(CourseCondition condition, Set<String> excludedPlaceIds) {
        List<CoursePlan> found = new ArrayList<>();
        // 반경을 넓히면 좁은 반경의 장소가 또 나온다 → 장소 ID 로 중복을 없앤다. LinkedHashMap 은 처음 넣은 순서를 지킨다
        Map<String, PlaceCandidate> seenCandidates = new LinkedHashMap<>();
        Set<Category> placeCategories = searchCategories(condition);

        List<Coordinate> centers = searchCenters(condition);

        for (int radius : radiusStepsFor(condition)) {
            List<PlaceCandidate> candidates;
            try {
                List<PlaceCandidate> all = findAround(centers, placeCategories, radius);
                all.forEach(candidate -> seenCandidates.putIfAbsent(candidate.placeId(), candidate));

                candidates = all.stream()
                        .filter(candidate -> !excludedPlaceIds.contains(candidate.placeId())) // 제외할 장소 빼기
                        .toList();
            } catch (CustomException e) {
                if (found.isEmpty()) {
                    throw e; // 보여줄 코스가 하나도 없으면 장소 오류를 그대로 전달 (명세 1.5)
                }
                // 좁은 반경에서 찾은 코스가 있으면 그것만이라도 보여준다 ("있는 만큼만", 명세 1.4)
                log.warn("반경 {}m 장소 검색 실패 → 이미 찾은 코스 {}개로 응답", radius, found.size());
                break;
            }

            found.addAll(courseComposer.compose(condition, candidates, found)); // 모자란 만큼만 더
            if (found.size() >= CourseComposer.MAX_COURSE_COUNT) {
                break;
            }
            log.info("반경 {}m 에서 코스 {}개 → 반경을 넓혀 다시 찾음", radius, found.size());
        }
        return new CourseSearchResult(found, List.copyOf(seenCandidates.values()));
    }

    /**
     * 후보를 찾을 지점들.
     * <pre>
     * 도착지가 없거나 출발지와 가까우면 → [출발지, 먼 지점]
     *     먼 지점 = 출발지에서 "조금 먼 코스" 목표 거리(최대 반경 × 70%)만큼, 무작위 방향으로 떨어진 곳
     *     도보 1.4km, 대중교통·차량 3.5km
     * 도착지가 멀면 (경로 모드)         → [출발지, 1/3 지점, 2/3 지점, 도착지]   가는 길 전체에서 찾는다
     * </pre>
     * 왜 여러 지점에서 찾는지:
     * 카카오 검색은 가까운 순으로 정해진 개수만 돌려준다. 가게가 많은 동네는 출발지 주변만으로 개수가 다 차서
     * 먼 장소가 후보에 아예 안 들어온다. 그러면 코스 조합에서 거리 비율(규칙 ⑫)을 줘도 고를 재료가 없다 (#204).
     * - 출발지에서만 찾으면 "출발지 근처에서 놀다가 도착지로 이동" 하는 코스만 나왔다 (#170)
     * - 중간 지점 하나만 두면 도착지 쪽(2/3 지점 근처) 후보가 모자랐다 (#204)
     * <p>
     * 경로 모드 판단은 코스 조합과 같은 기준을 쓰려고 CourseComposer.isRouteMode 에 맡긴다.
     * 지점 수만큼 장소 검색(카카오 로컬) 호출이 늘어난다 (NFR-014 무료 한도): 보통 2배, 경로 모드 4배.
     */
    List<Coordinate> searchCenters(CourseCondition condition) {
        Coordinate origin = condition.origin();
        if (!courseComposer.isRouteMode(condition)) {
            return List.of(origin, farCenter(condition));
        }
        Coordinate destination = condition.destination();
        return List.of(origin, pointBetween(origin, destination, 1.0 / 3), pointBetween(origin, destination, 2.0 / 3),
                destination);
    }

    /**
     * 출발지에서 "조금 먼 코스" 목표 거리만큼 떨어진 지점. 방향은 매번 무작위 (동서남북 어디로든).
     * 목표 거리는 코스 조합의 거리 비율 마지막 값(70%)을 그대로 써서 둘이 어긋나지 않게 한다.
     * <pre>
     * 위도 1도 ≈ 111km (어디서나 같음)
     * 경도 1도 ≈ 111km × cos(위도)  (서울 위도 37.5도 → 약 88km. 북쪽으로 갈수록 좁아진다)
     * 북쪽으로 d·cos(각도) km, 동쪽으로 d·sin(각도) km 를 각각 도(°)로 바꿔 더한다
     * </pre>
     */
    Coordinate farCenter(CourseCondition condition) {
        double ratio = CourseComposer.DISTANCE_RATIOS.get(CourseComposer.DISTANCE_RATIOS.size() - 1);
        double distanceKm = condition.transportType().getMaxSearchRadiusMeters() / 1000.0 * ratio;
        double angle = random.nextDouble() * 2 * Math.PI; // 0 ~ 360도 (라디안)

        Coordinate origin = condition.origin();
        double latitude = origin.latitude() + distanceKm * Math.cos(angle) / KM_PER_LATITUDE_DEGREE;
        double longitude = origin.longitude()
                + distanceKm * Math.sin(angle) / (KM_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(origin.latitude())));
        return new Coordinate(latitude, longitude);
    }

    /**
     * 출발지와 도착지 사이 지점. ratio 0 = 출발지, 1 = 도착지.
     * 몇 km 안쪽 짧은 거리라 위도·경도를 비율대로 나눠도 실제 지점과 거의 같다.
     */
    private Coordinate pointBetween(Coordinate from, Coordinate to, double ratio) {
        return new Coordinate(
                from.latitude() + (to.latitude() - from.latitude()) * ratio,
                from.longitude() + (to.longitude() - from.longitude()) * ratio);
    }

    /**
     * 여러 지점 주변 후보를 모아 하나의 목록으로 만든다. 지점끼리 반경이 겹쳐 같은 장소가 또 나오면 하나만 남긴다.
     * <p>
     * 일부 지점만 검색에 실패하면 나머지 지점 후보로 계속한다 (먼 지점 하나 때문에 추천 전체가 실패하지 않게, #204).
     * 모든 지점이 실패했을 때만 장소 쪽 오류(CustomException)를 그대로 올린다 (부르는 쪽에서 처리).
     */
    private List<PlaceCandidate> findAround(List<Coordinate> centers, Set<Category> placeCategories, int radius) {
        Map<String, PlaceCandidate> merged = new LinkedHashMap<>();
        CustomException lastFailure = null;
        int failures = 0;
        for (Coordinate center : centers) {
            try {
                nearbyPlaceService.findCandidates(center.latitude(), center.longitude(), placeCategories, radius)
                        .stream()
                        .map(this::toPlaceCandidate)
                        .forEach(candidate -> merged.putIfAbsent(candidate.placeId(), candidate));
            } catch (CustomException e) {
                log.warn("반경 {}m 장소 검색 실패 (지점 {}, {}) → 다른 지점 후보로 계속", radius,
                        center.latitude(), center.longitude());
                lastFailure = e;
                failures++;
            }
        }
        if (failures == centers.size()) {
            throw lastFailure; // 모든 지점 실패 → 장소 오류 그대로 (명세 1.5)
        }
        return List.copyOf(merged.values());
    }

    /**
     * 이 이동수단으로 넓혀 볼 반경 단계. 이동수단의 최대 반경을 넘는 단계는 뺀다.
     * 예) 도보(최대 2km) → [1km, 2km] / 대중교통·차량(최대 5km) → [1km, 2km, 3km, 5km]
     * <p>
     * 도보인데 5km 까지 넓히면 "편도 1시간 넘게 걸어가기" 같은 코스가 나와서 막는다 (#170).
     */
    List<Integer> radiusStepsFor(CourseCondition condition) {
        int maxRadius = condition.transportType().getMaxSearchRadiusMeters();
        return RADIUS_STEPS.stream()
                .filter(radius -> radius <= maxRadius)
                .toList();
    }

    /**
     * 장소 검색에 넘길 대분류. 랜덤이면 빈 Set (장소 쪽에서 "전체 대분류" 로 검색).
     * <p>
     * 추천과 장소가 같은 공통 enum(Category)을 쓰므로 바꿀 필요 없이 그대로 넘긴다 (#174).
     * 예전에는 enum 이 둘이라 이름(name())으로 하나씩 바꿔야 했다.
     */
    Set<Category> searchCategories(CourseCondition condition) {
        return condition.categories().isEmpty()
                ? EnumSet.noneOf(Category.class)
                : EnumSet.copyOf(condition.categories());
    }

    /**
     * 장소 쪽 후보(NearbyPlaceResult) → 추천 쪽 후보(PlaceCandidate).
     * 주소는 도로명을 먼저 쓰고, 없으면 지번 주소를 쓴다. 카카오맵 링크는 코스 상세에서 쓰려고 함께 담는다.
     */
    PlaceCandidate toPlaceCandidate(NearbyPlaceResult result) {
        String address = (result.roadAddress() != null) ? result.roadAddress() : result.address();
        return new PlaceCandidate(
                result.kakaoPlaceId(),
                result.name(),
                result.category(),
                result.subCategory(),
                address,
                result.latitude(),
                result.longitude(),
                result.placeUrl());
    }
}
