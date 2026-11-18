package com.teumteum.course.service;

import com.teumteum.course.domain.Course;
import com.teumteum.course.domain.CoursePlace;
import com.teumteum.course.domain.CourseRouteLeg;
import com.teumteum.course.dto.CourseSaveResponse;
import com.teumteum.course.exception.CourseErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.dto.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;

// 추천 응답을 검증하고 저장용 엔티티로 복사한다. 외부 API와 DB는 호출하지 않는다.
final class CourseSnapshotMapper {
    private CourseSnapshotMapper() {}

    static void validate(RecommendCourseDetailResponse d, String recommendationId) {
        require(d != null && recommendationId.equals(d.recommendationId()));
        require(text(d.courseName(), 810) && d.transportType() != null);
        require(d.origin() != null && location(d.origin()) && (d.destination() == null || location(d.destination())));
        require(d.startTime() != null && d.startTime().matches("(?:[01][0-9]|2[0-3]):(?:00|30)"));
        require(d.totalMinutes() >= 0 && d.totalMinutes() <= 360
                && d.totalTravelMinutes() >= 0 && d.totalTravelMinutes() <= d.totalMinutes()
                && d.finalTravelMinutes() >= 0 && d.finalTravelMinutes() <= d.totalTravelMinutes()
                && (d.destination() != null || d.finalTravelMinutes() == 0));
        require(d.places() != null && d.places().size() >= 1 && d.places().size() <= 4
                && d.placeCount() == d.places().size());
        var placeIds = new HashSet<String>();
        long stay = 0;
        long travel = d.finalTravelMinutes();
        for (int i = 0; i < d.places().size(); i++) {
            var p = d.places().get(i);
            require(p != null && text(p.placeId(), 150) && placeIds.add(p.placeId())
                    && p.visitOrder() == i + 1 && text(p.placeName(), 200)
                    && p.category() != null && text(p.subCategory(), 80)
                    && optional(p.address(), 500) && optional(p.imageUrl(), 2048)
                    && optional(p.transitInfo(), 200) && coordinate(p.latitude(), p.longitude())
                    && p.stayMinutes() >= 0 && p.stayMinutes() <= 360
                    && p.travelMinutes() >= 0 && p.travelMinutes() <= 360);
            stay += p.stayMinutes();
            travel += p.travelMinutes();
        }
        require(travel == d.totalTravelMinutes() && stay + travel == d.totalMinutes());
        require(d.routeLegs() != null
                && d.routeLegs().size() == d.places().size() + (d.destination() == null ? 0 : 1));
        for (int i = 0; i < d.routeLegs().size(); i++) {
            var leg = d.routeLegs().get(i);
            require(leg != null && leg.fromVisitOrder() == i && leg.toVisitOrder() == i + 1);
        }
        // 좌표 목록 전체를 저장 전에 검사해 부분 저장을 방지한다.
        routes(null, d.routeLegs());
    }

    static Course course(Long memberId, RecommendCourseDetailResponse d) {
        var o = d.origin();
        var end = d.destination();
        return Course.create(memberId, d.recommendationId(), d.courseName(), o.name(), o.address(),
                decimal(o.latitude()), decimal(o.longitude()),
                end == null ? null : end.name(), end == null ? null : end.address(),
                end == null ? null : decimal(end.latitude()), end == null ? null : decimal(end.longitude()),
                Course.TransportType.valueOf(d.transportType().name()), LocalTime.parse(d.startTime()),
                (short) d.totalMinutes(), (short) d.totalTravelMinutes(), (short) d.finalTravelMinutes(), d.estimated());
    }

    static CoursePlace place(Course course, Long internalPlaceId, RecommendPlaceDetailResponse p) {
        require(internalPlaceId != null && internalPlaceId > 0);
        return CoursePlace.create(course, internalPlaceId, p.placeName(), p.category().name(), p.subCategory(),
                p.address(), decimal(p.latitude()), decimal(p.longitude()), p.imageUrl(), p.description(),
                p.openingHours(), (short) p.visitOrder(), (short) p.stayMinutes(), (short) p.travelMinutes(), p.transitInfo());
    }

    static List<CourseRouteLeg> routes(Course course, List<RouteLegResponse> legs) {
        require(legs != null && !legs.isEmpty());
        return legs.stream().map(leg -> {
            require(leg != null && leg.fromVisitOrder() >= 0 && leg.fromVisitOrder() <= 4
                    && leg.toVisitOrder() == leg.fromVisitOrder() + 1 && leg.pathType() != null
                    && leg.coordinates() != null && leg.coordinates().size() >= 2);
            var coordinates = leg.coordinates().stream().map(c -> {
                require(c != null && coordinate(c.latitude(), c.longitude()));
                return new CourseRouteLeg.Coordinate(decimal(c.latitude()), decimal(c.longitude()));
            }).toList();
            return CourseRouteLeg.create(course, (short) leg.fromVisitOrder(), (short) leg.toVisitOrder(),
                    CourseRouteLeg.PathType.valueOf(leg.pathType().name()), coordinates);
        }).toList();
    }

    static CourseSaveResponse response(Course c, boolean created) {
        return new CourseSaveResponse(c.getCourseId().toString(), c.getCourseName(),
                c.getCreatedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), created);
    }

    private static boolean location(LocationResponse l) {
        return optional(l.name(), 200) && optional(l.address(), 500) && coordinate(l.latitude(), l.longitude());
    }
    private static boolean text(String s, int max) { return s != null && !s.isBlank() && s.length() <= max; }
    private static boolean optional(String s, int max) { return s == null || s.length() <= max; }
    private static boolean coordinate(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
    }
    private static BigDecimal decimal(double d) { return BigDecimal.valueOf(d); }
    private static void require(boolean valid) {
        if (!valid) throw new CustomException(CourseErrorCode.RECOMMENDATION_NOT_SAVEABLE);
    }
}
