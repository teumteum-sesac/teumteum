package com.teumteum.course.repository;

import com.teumteum.course.domain.CourseRouteLeg;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 경로 구간 엔티티를 다루며, PK인 courseRouteLegId의 타입은 Long이다.
public interface CourseRouteLegRepository extends JpaRepository<CourseRouteLeg, Long> {

    // 같은 코스의 모든 구간을 출발 순서대로 조회한다. Course_CourseId는 course.courseId를 의미한다.
    // 구간별 coordinates도 함께 읽는다. 결과가 없으면 빈 리스트를 반환한다.
    // Service에서 먼저 로그인 회원의 코스 소유권을 확인한 뒤 호출해야 한다.
    List<CourseRouteLeg> findByCourse_CourseIdOrderByFromVisitOrderAsc(Long courseId);
}
