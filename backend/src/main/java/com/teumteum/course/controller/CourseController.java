package com.teumteum.course.controller;

import com.teumteum.course.dto.CourseDeleteResponse;
import com.teumteum.course.dto.CourseListResponse;
import com.teumteum.course.dto.CourseResponse;
import com.teumteum.course.service.CourseService;
import com.teumteum.global.auth.LoginMember;
import com.teumteum.global.common.ApiResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HTTP 요청을 받아 Service에 전달하고, 결과를 공통 JSON 응답으로 반환한다.
@RestController // 메서드의 반환 객체를 JSON 응답 본문으로 변환한다.
@RequestMapping("/api/courses") // 이 Controller의 공통 주소다.
@RequiredArgsConstructor // final 필드를 받는 생성자를 만들어 CourseService를 주입받는다.
public class CourseController {

    private final CourseService courseService;

    // GET /api/courses?page=0&size=20: 본인의 저장 코스를 페이지 단위로 조회한다.
    @GetMapping
    public ResponseEntity<ApiResponse<CourseListResponse>> getCourses(
            // 기존 Resolver가 회원 ID를 채운다. 사용자가 쿼리로 지정하는 회원 ID가 아니다.
            // 로그인 토큰(Authorization: Bearer)을 JwtAuthenticationFilter 가 검증하고 회원 ID 를 넣어 준다.
            @LoginMember Long memberId,
            // URL에 page가 없으면 첫 페이지(0), size가 없으면 20개를 요청한다.
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        // 범위 검사(page >= 0, size 1~50), DB 조회, 장소 수 집계, DTO 변환은 Service가 담당한다.
        // 숫자가 아닌 page/size는 Spring이 변환 단계에서 거절한다.
        // 실패 시 예외는 GlobalExceptionHandler가 공통 오류 응답으로 처리한다.
        CourseListResponse response = courseService.getCourses(memberId, page, size);

        // 안쪽 ok(): {success: true, data: response, error: null} 형태로 감싼다.
        // 바깥쪽 ok(): HTTP 상태 코드를 200 OK로 지정한다. 빈 목록도 정상 응답이다.
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // GET /api/courses/1처럼 코스 ID를 지정해서 상세 조회한다.
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourse(
            @LoginMember Long memberId,
            @PathVariable("courseId") Long courseId
    ) {
        // Service가 본인 코스인지 확인하고 상세 응답을 만든다.
        CourseResponse response = courseService.getCourse(memberId, courseId);

        // 상세 데이터를 공통 응답으로 감싸 200 OK로 반환한다.
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // DELETE /api/courses/1처럼 코스 ID를 지정해 삭제한다.
    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDeleteResponse>> deleteCourse(
            // 기존 인증 계층에서 요청한 회원 ID를 받는다.
            @LoginMember Long memberId,

            // 주소에 포함된 삭제 대상 코스 ID를 받는다.
            @PathVariable("courseId") @Positive Long courseId
    ) {
        // Service에서 본인 코스 확인과 실제 삭제를 수행한다.
        CourseDeleteResponse response =
                courseService.deleteCourse(memberId, courseId);

        // 삭제 성공 결과를 공통 응답으로 감싸 200 OK로 반환한다.
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
