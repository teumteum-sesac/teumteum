package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.domain.PlaceCategory;
import com.teumteum.place.dto.NearbyPlaceResult;
import com.teumteum.place.service.NearbyPlaceService;
import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CourseCondition;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.PlaceCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 주변 장소 후보를 받아 코스를 찾는다. 코스가 3개가 안 되면 반경을 넓혀 다시 찾는다 (REQ-015, REQ-027).
 * <p>
 * 흐름 (명세 1.6 서버 처리 순서 3 ~ 6)
 * <pre>
 * 반경 1km → 후보 검색(장소 도메인) → 코스 조합(CourseComposer) → 3개 모이면 끝
 *          ↓ 모자라면
 * 반경 2km → 3km → 5km 차례로, 모자란 만큼만 더 찾는다
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
     * @return 코스 0 ~ 3개. 반경을 끝까지 넓혀도 없으면 빈 목록
     * @throws CustomException 장소 검색 외부 API 가 전부 실패하고, 그때까지 찾은 코스도 없을 때 (장소 쪽 502·503 그대로 전달)
     */
    public List<CoursePlan> findCourses(CourseCondition condition) {
        List<CoursePlan> found = new ArrayList<>();
        Set<PlaceCategory> placeCategories = toPlaceCategories(condition);

        for (int radius : RADIUS_STEPS) {
            List<PlaceCandidate> candidates;
            try {
                candidates = nearbyPlaceService.findCandidates(
                        condition.origin().latitude(), condition.origin().longitude(), placeCategories, radius)
                        .stream()
                        .map(this::toPlaceCandidate)
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
        return found;
    }

    /**
     * 추천 쪽 대분류(CategoryCode) → 장소 쪽 대분류(PlaceCategory).
     * 두 enum 은 값 이름이 같아서 이름(name())으로 바꾼다. 랜덤이면 빈 Set (장소 쪽에서 "전체 대분류" 로 검색).
     * <p>
     * 이름이 다르게 바뀌면 valueOf 에서 바로 오류가 나서 알아챌 수 있다 (조용히 틀린 값이 들어가지 않음).
     */
    Set<PlaceCategory> toPlaceCategories(CourseCondition condition) {
        Set<PlaceCategory> result = EnumSet.noneOf(PlaceCategory.class);
        for (CategoryCode category : condition.categories()) {
            result.add(PlaceCategory.valueOf(category.name()));
        }
        return result;
    }

    /**
     * 장소 쪽 후보(NearbyPlaceResult) → 추천 쪽 후보(PlaceCandidate).
     * 주소는 도로명을 먼저 쓰고, 없으면 지번 주소를 쓴다.
     */
    PlaceCandidate toPlaceCandidate(NearbyPlaceResult result) {
        String address = (result.roadAddress() != null) ? result.roadAddress() : result.address();
        return new PlaceCandidate(
                result.kakaoPlaceId(),
                result.name(),
                CategoryCode.valueOf(result.category().name()),
                result.subCategory(),
                address,
                result.latitude(),
                result.longitude());
    }
}
