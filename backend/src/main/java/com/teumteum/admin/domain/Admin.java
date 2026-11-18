package com.teumteum.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/*
 * [이 파일이 하는 일]
 * 관리자 명단 (DB admin 테이블, docs/erd.md). 회원 중 이 테이블에 있는 사람만 관리자다 (REQ-044).
 *
 * [왜 member 에 role 컬럼을 두지 않았나]
 *   ERD 설계(김하윤)대로 관리자는 별도 테이블. 관리자 지정·해제가 회원 정보와 섞이지 않는다.
 *
 * [관리자 지정 방법] — 관리자 가입 화면은 없다
 *   로컬·시연 DB 에서 SQL 한 줄: INSERT INTO admin (member_id) VALUES (회원번호);
 *   지정 후에는 다시 로그인해야 토큰에 관리자 표시가 들어간다 (JwtProvider.createAccessToken).
 */
@Entity
@Table(name = "admin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin {

    /** 관리자인 회원의 ID (member.member_id 와 같은 값, 기본 키) */
    @Id
    @Column(name = "member_id")
    private Long memberId;

    /** 관리자로 지정된 시각 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    private Admin(Long memberId) {
        this.memberId = memberId;
    }

    public static Admin of(Long memberId) {
        return new Admin(memberId);
    }
}
