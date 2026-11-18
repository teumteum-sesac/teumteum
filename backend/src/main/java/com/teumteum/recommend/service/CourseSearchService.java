package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.global.common.category.Category;
import com.teumteum.place.dto.NearbyPlaceResult;
import com.teumteum.place.service.NearbyPlaceService;
import com.teumteum.global.common.category.Category;
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
import java.util.Set;

/**
 * 주변 장소 후보를 받아 코스를 찾는다. 코스가 3개가 안 되면 반경을 넓혀 다시 찾는다 (REQ-015, REQ-027).
 * <p>
 * 흐름 (명세 1.6 서버 처리 순서 3 ~ 6)
 * <pre>
 * 반경 1km → 후보 검색(장소 도메인) → 코스 조합(CourseComposer) → 3개 모이면 끝
 *            (도착지가 멀면 출발지·중간·도착지 3곳 주변에서 찾아 합친다)
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

    private final NearbyPlaceService nearbyPlaceService;
    private final CourseComposer courseComposer;

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
     * 도착지가 없거나 출발지와 가까우면 → [출발지]
     * 도착지가 멀면 (경로 모드)         → [출발지, 중간 지점, 도착지]   가는 길 전체에서 찾는다
     * </pre>
     * 출발지에서만 찾으면 "출발지 근처에서 놀다가 도착지로 이동" 하는 코스만 나와서 바꿨다 (#170).
     * 경로 모드 판단은 코스 조합과 같은 기준을 쓰려고 CourseComposer.isRouteMode 에 맡긴다.
     * <p>
     * 지점이 3개면 장소 검색(카카오 로컬) 호출도 3배가 된다 (NFR-014 무료 한도). 그래서 도착지가 멀 때만 쓴다.
     */
    List<Coordinate> searchCenters(CourseCondition condition) {
        if (!courseComposer.isRouteMode(condition)) {
            return List.of(condition.origin());
        }
        Coordinate origin = condition.origin();
        Coordinate destination = condition.destination();
        // 몇 km 안쪽 짧은 거리라 위도·경도를 그냥 평균 내도 실제 중간과 거의 같다
        Coordinate middle = new Coordinate(
                (origin.latitude() + destination.latitude()) / 2,
                (origin.longitude() + destination.longitude()) / 2);
        return List.of(origin, middle, destination);
    }

    /**
     * 여러 지점 주변 후보를 모아 하나의 목록으로 만든다. 지점끼리 반경이 겹쳐 같은 장소가 또 나오면 하나만 남긴다.
     * 한 지점이라도 검색에 실패하면 장소 쪽 오류(CustomException)가 그대로 올라간다 (부르는 쪽에서 처리).
     */
    private List<PlaceCandidate> findAround(List<Coordinate> centers, Set<Category> placeCategories, int radius) {
        Map<String, PlaceCandidate> merged = new LinkedHashMap<>();
        for (Coordinate center : centers) {
            nearbyPlaceService.findCandidates(center.latitude(), center.longitude(), placeCategories, radius)
                    .stream()
                    .map(this::toPlaceCandidate)
                    .forEach(candidate -> merged.putIfAbsent(candidate.placeId(), candidate));
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
