package com.teumteum.course.repository;

import com.teumteum.course.domain.CoursePlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// CoursePlace 엔티티를 다루며, PK인 coursePlaceId의 타입은 Long이다.
// 기본 저장·조회·삭제 기능은 JpaRepository에서 상속받는다.
public interface CoursePlaceRepository extends JpaRepository<CoursePlace, Long> {

    // 특정 코스에 포함된 장소들을 방문 순서(visitOrder) 오름차순으로 조회한다.
    // Course_CourseId: CoursePlace의 course 객체 안에 있는 courseId를 조회 조건으로 사용한다.
    // coursePlaceId는 장소 행 하나의 PK이고, 여기서는 같은 코스에 속한 여러 행을 찾으므로 코스 ID로 조회한다.
    // OrderByVisitOrderAsc: visitOrder가 1 → 2 → 3 순서가 되도록 정렬한다.
    // List<CoursePlace>: 장소들을 페이지 구분 없이 반환하며, 조회 결과가 없으면 빈 리스트를 반환한다.
    // 회원 소유권은 이 메서드가 검사하지 않는다. Service에서 본인 코스인지 먼저 확인한 뒤 호출해야 한다.
    List<CoursePlace> findByCourse_CourseIdOrderByVisitOrderAsc(Long courseId);
}
