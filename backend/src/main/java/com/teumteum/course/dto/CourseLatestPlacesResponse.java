package com.teumteum.course.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CourseLatestPlacesResponse(String courseId, OffsetDateTime requestedAt, List<CourseLatestPlaceResponse> places) {
    public CourseLatestPlacesResponse { places = List.copyOf(places); }
}
