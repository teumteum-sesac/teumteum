package com.teumteum.member.repository;

import com.teumteum.member.domain.Member;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/*
 * [이 파일이 하는 일]
 * member 테이블을 조회·저장하는 창구. service 는 SQL 을 직접 쓰지 않고 이 인터페이스의 메서드를 부른다.
 *
 * [인터페이스만 있고 구현 코드가 없는데 어떻게 동작하나]
 *   Spring Data JPA 가 서버를 켤 때 이 인터페이스를 보고 구현 객체를 자동으로 만들어 준다.
 *
 *   1) JpaRepository<Member, Long> 를 상속하면 기본 메서드가 공짜로 생긴다
 *        save(member)      → INSERT 또는 UPDATE
 *        findById(id)      → SELECT ... WHERE member_id = ?
 *        findAll()         → SELECT * FROM member
 *        delete(member)    → DELETE
 *      <Member, Long> = <다룰 엔티티, 그 엔티티의 PK 타입>
 *
 *   2) 메서드 "이름 규칙"만 지키면 쿼리를 자동으로 만들어 준다 (쿼리 메서드)
 *        findByEmail     → SELECT * FROM member WHERE email = ?
 *        existsByEmail   → SELECT 1 FROM member WHERE email = ? LIMIT 1 (있으면 true)
 *      find·exists·count 같은 동작 + By + 필드 이름(Member 의 필드 이름과 똑같이)
 *
 * 다른 도메인(course, admin 등)은 이 repository 를 직접 부르지 않고 member 의 service 를 통해 쓴다 (AGENTS.md 5장).
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    // 저장·탈퇴가 같은 회원 행을 먼저 잠그도록 해 탈퇴 직후 새 코스가 남는 경쟁을 막는다.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :memberId")
    Optional<Member> findByIdForUpdate(@org.springframework.data.repository.query.Param("memberId") Long memberId);

    /**
     * 이메일로 회원 찾기 — 로그인할 때 사용.
     * Optional: 결과가 없을 수도 있다는 뜻. null 대신 Optional.empty() 가 돌아와서 null 실수를 줄인다.
     */
    Optional<Member> findByEmail(String email);

    /** 이메일이 이미 가입돼 있는지 — 회원가입 중복 확인(REQ-001)에 사용 */
    boolean existsByEmail(String email);

    /** 관리자 회원 목록 검색 (REQ-045): 이메일 또는 닉네임에 검색어가 들어간 회원. 최신 가입순은 Pageable 의 정렬로 */
    Page<Member> findByEmailContainingIgnoreCaseOrNicknameContainingIgnoreCase(String email, String nickname, Pageable pageable);
}
