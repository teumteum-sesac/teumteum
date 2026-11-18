package com.teumteum.course.service;

import com.teumteum.recommend.domain.CategoryCode;
import com.teumteum.recommend.domain.PathType;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.dto.*;
import java.time.OffsetDateTime;
import java.util.List;

final class CourseSaveFixtures {
    static RecommendCourseDetailResponse detail(String id, String externalPlaceId) {
        return detail(id, externalPlaceId, "카페");
    }

    static RecommendCourseDetailResponse detail(String id, String externalPlaceId, String subCategory) {
        var place = new RecommendPlaceDetailResponse(externalPlaceId, 1, "저장 카페", CategoryCode.CAFE,
                subCategory, "저장 주소", 37.5665, 126.978, "https://example.com/photo.jpg", "09:00~18:00",
                50, 10, null, "저장 소개", "https://place.map.kakao.com/" + externalPlaceId);
        var route = new RouteLegResponse(0, 1, PathType.STRAIGHT,
                List.of(new CoordinateResponse(37.565, 126.977), new CoordinateResponse(37.5665, 126.978)));
        return new RecommendCourseDetailResponse(id, "set-test", OffsetDateTime.now().plusHours(2),
                new LocationResponse("출발지", null, 37.565, 126.977), null, TransportType.WALK, 120,
                "저장 카페", 1, "13:00", 60, 10, 0, true, List.of(place), List.of(route));
    }
}
