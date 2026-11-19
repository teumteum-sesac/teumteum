package com.teumteum.admin.repository;

import com.teumteum.admin.domain.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 공지사항 조회. 목록은 최신 등록순 */
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
