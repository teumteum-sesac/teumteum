package com.teumteum.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/*
 * [이 파일이 하는 일]
 * 공지사항 한 건 (DB notice 테이블). 관리자가 등록·수정·삭제하고 누구나 조회한다 (REQ-049).
 * 전체 알림(REQ-053)은 별도 테이블 없이 "가장 최근 공지를 홈 상단 배너로 띄우는 것"으로 대신한다.
 */
@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    public static final int TITLE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    /** 등록한 관리자 회원 ID (admin.member_id) */
    @Column(name = "admin_member_id", nullable = false)
    private Long adminMemberId;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    /** 본문 (TEXT) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Notice(Long adminMemberId, String title, String content) {
        this.adminMemberId = adminMemberId;
        this.title = title;
        this.content = content;
    }

    public static Notice create(Long adminMemberId, String title, String content) {
        return new Notice(adminMemberId, title, content);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
