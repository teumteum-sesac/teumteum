package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.PlaceAlternative;
import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import com.teumteum.recommend.dto.AlternativePlaceListResponse;
import com.teumteum.recommend.exception.RecommendErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 장소 교체 (DET-03, 명세 4·5장). 6-1 은 후보 조회, 6-2 에서 교체 확정을 더한다.
 * <p>
 * RecommendService 에 넣지 않고 따로 둔 이유: 추천 생성·다시 추천·상세만으로도 RecommendService 가 길다.
 * 교체는 "코스 하나 안의 장소 하나" 를 다루는 별도 기능이라 나눠 두면 읽기도, 테스트하기도 쉽다.
 * 코스를 꺼내는 규칙(404·410)은 RecommendService 의 것을 그대로 써서 두 곳이 어긋나지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceReplaceService {

    private final RecommendService recommendService;
    private final AlternativeFinder alternativeFinder;
    private final RecommendResponseMapper responseMapper;

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

    /** 코스 안에서 장소의 위치(0부터). 코스에 없는 장소면 404 (명세 4장) */
    int indexOf(CoursePlan plan, String placeId) {
        for (int i = 0; i < plan.stops().size(); i++) {
            if (plan.stops().get(i).place().placeId().equals(placeId)) {
                return i;
            }
        }
        throw new CustomException(RecommendErrorCode.RECOMMENDATION_NOT_FOUND);
    }
}
