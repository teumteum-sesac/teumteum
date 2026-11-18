package com.teumteum.course.repository;

import com.teumteum.course.domain.Course;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// Course 엔티티를 다루고 PK 타입은 Long이다. Spring Data JPA가 실행 시 구현 객체를 제공한다.
// JpaRepository에서 기본 저장(save), 조회(findById), 삭제(delete) 등의 기능을 상속받는다.
public interface CourseRepository extends JpaRepository<Course, Long> {

    // 코스 ID와 회원 ID가 모두 일치하는 한 건을 조회한다. 메서드 이름은 Course의 필드명을 따른다.
    // Optional<Course>: 결과가 있으면 Course를 담고, 없거나 다른 회원 소유이면 빈 결과를 반환한다.
    // memberId에는 인증 계층에서 확인한 로그인 회원 ID를 전달해야 한다.
    Optional<Course> findByCourseIdAndMemberId(Long courseId, Long memberId);

    // 해당 회원의 코스를 페이지 단위로 조회한다. Pageable로 페이지 번호(0부터), 크기, 정렬을 전달한다.
    // Page<Course>는 Course 목록과 전체 건수·페이지 수를 담는다. 조회 결과가 없으면 목록이 비어 있다.
    Page<Course> findByMemberId(Long memberId, Pageable pageable);

    Optional<Course> findByMemberIdAndSourceRecommendationId(
            Long memberId,
            String sourceRecommendationId);
}
