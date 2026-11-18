package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.RecommendCondition;
import com.teumteum.recommend.domain.RecommendCondition.Location;
import com.teumteum.recommend.domain.RecommendationSet;
import com.teumteum.recommend.domain.RecommendedCourse;
import com.teumteum.recommend.domain.SubCategory;
import com.teumteum.recommend.dto.LocationResponse;
import com.teumteum.recommend.dto.RecommendConditionResponse;
import com.teumteum.recommend.dto.RecommendCourseResponse;
import com.teumteum.recommend.dto.RecommendPlaceResponse;
import com.teumteum.recommend.dto.RecommendResponse;
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
            for (CategoryCode category : condition.categories()) {
                filters.add(filterOf(category, SubCategory.of(category)));
            }
            return filters;
        }

        Set<SubCategory> appeared = subCategoriesIn(courses);
        for (CategoryCode category : CategoryCode.values()) {
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

    private SubCategoryFilterResponse filterOf(CategoryCode category, List<SubCategory> subs) {
        List<String> names = subs.stream().map(SubCategory::getDisplayName).toList();
        return new SubCategoryFilterResponse(category, category.getDisplayName(), names);
    }
}
