package com.teumteum.course.service;

import com.teumteum.course.domain.Course;
import com.teumteum.course.domain.CoursePlace;
import com.teumteum.course.domain.CourseRouteLeg;
import com.teumteum.course.dto.*;
import com.teumteum.course.exception.CourseErrorCode;
import com.teumteum.course.repository.CoursePlaceCount;
import com.teumteum.course.repository.CoursePlaceRepository;
import com.teumteum.course.repository.CourseRepository;
import com.teumteum.course.repository.CourseRouteLegRepository;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.teumteum.recommend.service.RecommendService;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import com.teumteum.recommend.dto.RouteLegResponse;


@Service // Spring이 관리하는 서비스 객체로 등록한다.
@RequiredArgsConstructor // final 필드를 받는 생성자를 만들고 Repository를 주입받는다.
@Transactional(readOnly = true) // 기본적으로 저장본을 변경하지 않는 조회 작업이다.
public class CourseService {

    private final CourseRepository courseRepository;
    private final CoursePlaceRepository coursePlaceRepository;
    private final CourseRouteLegRepository courseRouteLegRepository;
    private final RecommendService recommendService;

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

    // 클래스의 readOnly=true 설정을 덮어써 저장 작업을 쓰기 트랜잭션으로 실행한다.
    @Transactional
    public CourseSaveResponse saveCourse(Long memberId, String recommendationId) {
        // 회원 ID는 Controller가 인증 정보에서 전달한다. 잘못된 회원 ID로 DB를 조회하지 않는다.
        if (memberId == null || memberId <= 0) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        // Controller의 DTO 검증과 별개로 Service에서도 잘못된 추천 ID를 거절한다.
        if (recommendationId == null || recommendationId.isBlank() || recommendationId.length() > 128) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        // 동일 회원이 동일 추천 버전을 이미 저장했는지 확인한다.
        // Optional은 조회 결과가 있을 수도, 없을 수도 있음을 나타낸다.
        Optional<Course> existingCourse =
                courseRepository.findByMemberIdAndSourceRecommendationId(memberId, recommendationId);

        // isPresent()가 true면 이미 저장된 코스가 있다는 뜻이다.
        // 추천 조회보다 먼저 처리하므로 원래 추천이 만료돼도 기존 저장본을 반환할 수 있다.
        if (existingCourse.isPresent()) {
            Course course = existingCourse.get(); // Optional 안에 들어 있는 실제 코스 객체를 꺼낸다.
            String createdAt = course.getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            // 새로 저장하지 않았으므로 created는 false다. ID·이름·저장 시각은 기존 값을 유지한다.
            // return하면 이 메서드가 끝나므로 아래 신규 저장 경로로 넘어가지 않는다.
            return new CourseSaveResponse(
                    course.getCourseId().toString(),
                    course.getCourseName(),
                    createdAt,
                    false
            );
        }

        // 기존 저장본이 없을 때만 추천 서비스에서 장소·일정·경로가 담긴 상세 정보를 받는다.
        // 추천이 없으면 404, 만료됐으면 410 예외가 발생하며 저장 처리는 진행하지 않는다.
        RecommendCourseDetailResponse detail =
                recommendService.getCourseDetail(recommendationId);

        // 추천 응답의 위치 정보를 꺼낸다. 도착지는 설정하지 않았으면 null이다.
        var origin = detail.origin();
        var destination = detail.destination();

        // Course.create()는 값을 채운 엔티티 객체만 만든다. 이 시점에는 DB에 저장하지 않는다.
        // 추천 DTO의 double 좌표는 BigDecimal, "HH:mm" 문자열은 LocalTime으로 변환한다.
        Course course = Course.create(
                memberId,
                detail.recommendationId(),
                detail.courseName(),
                origin.name(),
                origin.address(),
                BigDecimal.valueOf(origin.latitude()),
                BigDecimal.valueOf(origin.longitude()),
                destination == null ? null : destination.name(),
                destination == null ? null : destination.address(),
                destination == null ? null : BigDecimal.valueOf(destination.latitude()),
                destination == null ? null : BigDecimal.valueOf(destination.longitude()),
                Course.TransportType.valueOf(detail.transportType().name()),
                LocalTime.parse(detail.startTime()),
                toMinutes(detail.totalMinutes()),
                toMinutes(detail.totalTravelMinutes()),
                toMinutes(detail.finalTravelMinutes()),
                detail.estimated()
        );

        // 추천의 경로 구간과 좌표를 저장용 엔티티로 복사하고 같은 코스 객체에 연결한다.
        // 여기서는 객체만 생성한다. 아직 코스와 경로를 DB에 저장하지 않는다.
        List<CourseRouteLeg> routeLegs = createRouteLegs(course, detail.routeLegs());

        // TODO: 장소 담당 서비스에서 카카오 장소 ID에 대응하는 내부 place_id를 받은 뒤,
        // CoursePlace를 생성하고 코스·장소·위에서 만든 routeLegs를 함께 저장한다.
        // [임시] 추천의 placeId는 카카오 ID이고 DB의 place_id는 별도 내부 ID다.
        // 장소 ID 연결이 준비될 때까지 신규 저장을 중단해 불완전한 코스가 저장되지 않게 한다.
        throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 구간 목록의 순서와 각 구간 안의 좌표 순서를 유지하며 저장용 객체 목록을 만든다.
    List<CourseRouteLeg> createRouteLegs(Course course, List<RouteLegResponse> routeLegs) {
        if (routeLegs == null || routeLegs.isEmpty()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        return routeLegs.stream().map(leg -> {
            // 출발지 0, 장소 1~4, 도착지 최대 5다. 한 구간은 바로 다음 지점으로 이어진다.
            if (leg == null || leg.fromVisitOrder() < 0 || leg.fromVisitOrder() > 4
                    || leg.toVisitOrder() != leg.fromVisitOrder() + 1
                    || leg.pathType() == null || leg.coordinates() == null
                    || leg.coordinates().size() < 2) {
                throw new CustomException(CommonErrorCode.INVALID_REQUEST);
            }

            List<CourseRouteLeg.Coordinate> coordinates = leg.coordinates().stream()
                    .map(coordinate -> {
                        // NaN·무한대와 지도 범위를 벗어난 좌표는 저장할 수 없다.
                        if (coordinate == null || !Double.isFinite(coordinate.latitude())
                                || !Double.isFinite(coordinate.longitude())
                                || Math.abs(coordinate.latitude()) > 90
                                || Math.abs(coordinate.longitude()) > 180) {
                            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
                        }
                        return new CourseRouteLeg.Coordinate(
                                BigDecimal.valueOf(coordinate.latitude()),
                                BigDecimal.valueOf(coordinate.longitude())
                        );
                    }).toList();

            // 추천 도메인과 저장 도메인의 enum은 별도 타입이므로 같은 이름으로 변환한다.
            // 위에서 순서 범위를 검사했으므로 Short로 변환해도 값이 넘치지 않는다.
            return CourseRouteLeg.create(
                    course,
                    (short) leg.fromVisitOrder(),
                    (short) leg.toVisitOrder(),
                    CourseRouteLeg.PathType.valueOf(leg.pathType().name()),
                    coordinates
            );
        }).toList();
    }

    // int를 무조건 short로 바꾸면 큰 값이 음수로 바뀔 수 있어 범위를 검사한 뒤 변환한다.
    private short toMinutes(int minutes) {
        if (minutes < 0 || minutes > Short.MAX_VALUE) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        return (short) minutes;
    }
}
