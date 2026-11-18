package com.teumteum.course.service;

import com.teumteum.course.domain.Course;
import com.teumteum.course.domain.CoursePlace;
import com.teumteum.course.domain.CourseRouteLeg;
import com.teumteum.course.dto.CourseCoordinateResponse;
import com.teumteum.course.dto.CourseDeleteResponse;
import com.teumteum.course.dto.CourseListResponse;
import com.teumteum.course.dto.CourseLocationResponse;
import com.teumteum.course.dto.CoursePlaceResponse;
import com.teumteum.course.dto.CourseResponse;
import com.teumteum.course.dto.CourseRouteLegResponse;
import com.teumteum.course.dto.CourseSummaryResponse;
import com.teumteum.course.exception.CourseErrorCode;
import com.teumteum.course.repository.CoursePlaceCount;
import com.teumteum.course.repository.CoursePlaceRepository;
import com.teumteum.course.repository.CourseRepository;
import com.teumteum.course.repository.CourseRouteLegRepository;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // Spring이 관리하는 서비스 객체로 등록한다.
@RequiredArgsConstructor // final 필드를 받는 생성자를 만들고 Repository를 주입받는다.
@Transactional(readOnly = true) // 기본적으로 저장본을 변경하지 않는 조회 작업이다.
public class CourseService {

    private final CourseRepository courseRepository;
    private final CoursePlaceRepository coursePlaceRepository;
    private final CourseRouteLegRepository courseRouteLegRepository;

    // memberId는 요청 파라미터가 아니라 Controller가 인증 정보에서 확인한 회원 ID여야 한다.
    public CourseListResponse getCourses(Long memberId, int page, int size) {
        if (memberId == null || memberId <= 0) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        // PageRequest를 만들기 전에 명세의 범위를 검사해 잘못된 요청은 400으로 처리한다.
        if (page < 0 || size < 1 || size > 50) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        // 저장 시각이 같아도 코스 ID로 순서를 정한다. 정렬 기준은 사용자가 변경하지 않는다.
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("courseId")
        ));
        Page<Course> coursePage = courseRepository.findByMemberId(memberId, pageable);

        // 저장 코스가 없거나 마지막 페이지를 넘겼으면 집계 쿼리를 실행하지 않는다.
        // 페이지가 비어도 전체 건수는 남아 있을 수 있으므로 Page의 메타데이터를 그대로 사용한다.
        if (coursePage.isEmpty()) {
            return toListResponse(coursePage, List.of());
        }

        List<Long> courseIds = coursePage.getContent().stream()
                .map(Course::getCourseId)
                .toList();

        // 본인의 현재 페이지에 포함된 코스만 집계한다. 코스별로 추가 조회하지 않는다.
        // 결과를 Map으로 바꾸면 코스 ID로 장소 수를 바로 찾을 수 있다.
        Map<Long, Long> placeCounts = coursePlaceRepository.countPlacesByCourseIds(courseIds)
                .stream()
                .collect(Collectors.toMap(
                        CoursePlaceCount::getCourseId,
                        CoursePlaceCount::getPlaceCount
                ));

        List<CourseSummaryResponse> content = coursePage.getContent().stream()
                .map(course -> toSummaryResponse(course, placeCounts))
                .toList();

        return toListResponse(coursePage, content);
    }

    private CourseSummaryResponse toSummaryResponse(Course course, Map<Long, Long> placeCounts) {
        Long placeCount = placeCounts.get(course.getCourseId());
        // 저장 코스는 장소 1~4개가 필수다. 잘못된 저장본을 정상적인 카드로 반환하지 않는다.
        if (placeCount == null || placeCount < 1 || placeCount > 4) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        // Course는 LocalDateTime.now()로 저장하므로 현재 저장 구현과 같은 JVM 기본 시간대를 쓴다.
        // 기존 저장본을 올바르게 해석하려면 저장 서버와 조회 서버의 기본 시간대가 같아야 한다.
        String createdAt = course.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new CourseSummaryResponse(
                course.getCourseId().toString(),
                course.getSourceRecommendationId(),
                course.getCourseName(),
                Math.toIntExact(placeCount), // count()의 Long을 응답의 Integer로 변환한다.
                course.getTotalMinutes().intValue(), // 엔티티의 Short를 Integer로 변환한다.
                createdAt
        );
    }

    private CourseListResponse toListResponse(Page<Course> coursePage, List<CourseSummaryResponse> content) {
        // 전체 건수와 페이지 수는 현재 목록의 길이로 계산하지 않고 Page에서 가져온다.
        return new CourseListResponse(
                content,
                coursePage.getNumber(),
                coursePage.getSize(),
                coursePage.getTotalElements(),
                coursePage.getTotalPages(),
                coursePage.hasNext()
        );
    }

    // 로그인 회원이 소유한 코스만 조회한다.
    private Course findOwnedCourse(Long memberId, Long courseId) {
        // 회원 ID가 올바르지 않으면 조회하지 않는다.
        if (memberId == null || memberId <= 0) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }

        // 코스 ID는 양수여야 한다.
        if (courseId == null || courseId <= 0) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        // 코스 ID와 회원 ID가 모두 일치하는 데이터를 찾는다.
        // 없거나 다른 회원의 코스라면 동일하게 404 오류를 발생시킨다.
        return courseRepository.findByCourseIdAndMemberId(courseId, memberId)
                .orElseThrow(() ->
                        new CustomException(CourseErrorCode.COURSE_NOT_FOUND)
                );
    }

    // 저장 당시의 장소 정보와 일정을 응답 DTO로 변환한다.
    // 최신 장소 API를 호출하지 않고 CoursePlace에 저장된 값을 사용한다.
    private CoursePlaceResponse toPlaceResponse(CoursePlace place) {
        return new CoursePlaceResponse(
                place.getCoursePlaceId().toString(),
                place.getPlaceId().toString(),
                place.getVisitOrder().intValue(),
                place.getPlaceName(),
                place.getCategoryCode(),
                place.getSubCategoryName(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getImageUrl(),
                place.getDescription(),
                place.getOpeningHours(),
                place.getStayMinutes().intValue(),
                place.getTravelMinutes().intValue(),
                place.getTransitInfo()
        );
    }

    // 저장된 경로 한 구간과 그 안의 좌표들을 응답 DTO로 변환한다.
    private CourseRouteLegResponse toRouteLegResponse(CourseRouteLeg leg) {

        // 엔티티의 좌표 목록을 응답용 좌표 목록으로 바꾼다.
        // stream은 목록을 순서대로 처리하며, 좌표의 진행 방향을 유지한다.
        List<CourseCoordinateResponse> coordinates = leg.getCoordinates()
                .stream()
                .map(coordinate -> new CourseCoordinateResponse(
                        coordinate.latitude(),
                        coordinate.longitude()
                ))
                .toList();

        return new CourseRouteLegResponse(
                leg.getFromVisitOrder().intValue(),
                leg.getToVisitOrder().intValue(),
                leg.getPathType().name(),
                coordinates
        );
    }

    // 본인 소유의 저장 코스를 상세 응답으로 반환한다.
    public CourseResponse getCourse(Long memberId, Long courseId) {

        // 1. 본인의 코스인지 먼저 확인한다.
        // 없거나 다른 회원의 코스면 여기서 중단된다.
        Course course = findOwnedCourse(memberId, courseId);

        // 2. 저장 장소들을 방문 순서대로 조회하고 DTO로 변환한다.
        List<CoursePlaceResponse> places = coursePlaceRepository
                .findByCourse_CourseIdOrderByVisitOrderAsc(courseId)
                .stream()
                .map(this::toPlaceResponse)
                .toList();

        // 3. 저장된 경로를 출발 순서대로 조회하고 DTO로 변환한다.
        List<CourseRouteLegResponse> routeLegs = courseRouteLegRepository
                .findByCourse_CourseIdOrderByFromVisitOrderAsc(courseId)
                .stream()
                .map(this::toRouteLegResponse)
                .toList();

        // 4. 저장 당시 출발지 정보를 만든다.
        CourseLocationResponse origin = new CourseLocationResponse(
                course.getOriginName(),
                course.getOriginAddress(),
                course.getOriginLatitude(),
                course.getOriginLongitude()
        );

        // 도착지는 선택 사항이다. 설정하지 않았다면 null을 반환한다.
        CourseLocationResponse destination = null;

        if (course.getDestinationLatitude() != null) {
            destination = new CourseLocationResponse(
                    course.getDestinationName(),
                    course.getDestinationAddress(),
                    course.getDestinationLatitude(),
                    course.getDestinationLongitude()
            );
        }

        // 5. 목록 조회와 동일하게 JVM 기본 시간대로 저장 시각을 해석한다.
        String createdAt = course.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // 6. 코스 기본 정보, 장소 목록, 경로 목록을 하나의 응답으로 묶는다.
        return new CourseResponse(
                course.getCourseId().toString(),
                course.getSourceRecommendationId(),
                course.getCourseName(),
                createdAt,
                origin,
                destination,
                course.getTransportType().name(),
                course.getStartTime().format(
                        DateTimeFormatter.ofPattern("HH:mm")
                ),
                course.getTotalMinutes().intValue(),
                course.getTotalTravelMinutes().intValue(),
                course.getFinalTravelMinutes().intValue(),
                course.isEstimated(),
                places,
                routeLegs
        );
    }

    // 탈퇴 시 호출된다. 호출한 회원 서비스의 트랜잭션에 참여하며 공용 장소는 삭제하지 않는다.
    @Transactional
    public void deleteMemberCourses(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        courseRepository.deleteAllByMemberId(memberId);
    }

    // 클래스의 읽기 전용 설정을 덮어써서 삭제를 쓰기 트랜잭션으로 실행한다.
    @Transactional
    public CourseDeleteResponse deleteCourse(Long memberId, Long courseId) {
        // 없거나 다른 회원 소유의 코스는 삭제 전에 기존 검증에서 거절한다.
        Course course = findOwnedCourse(memberId, courseId);

        // 실제 DB의 ON DELETE CASCADE 설정으로 저장 장소·경로도 함께 삭제된다.
        // 공용 장소(place)는 삭제하지 않는다.
        courseRepository.delete(course);

        // 트랜잭션 커밋에 실패하면 정상 응답 대신 예외가 전달된다.
        return new CourseDeleteResponse(course.getCourseId().toString(), true);
    }
}
