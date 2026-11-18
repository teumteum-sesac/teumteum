package com.teumteum.course.dto;

import com.teumteum.place.dto.LatestPlaceResponse;
import java.time.OffsetDateTime;

public record CourseLatestPlaceResponse(
        String coursePlaceId, String placeId, int visitOrder, String status,
        OffsetDateTime fetchedAt, LatestPlaceResponse place, String errorCode, String message
) {
}
