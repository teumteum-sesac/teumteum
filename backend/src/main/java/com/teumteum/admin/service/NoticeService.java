package com.teumteum.admin.service;

import com.teumteum.admin.domain.Notice;
import com.teumteum.admin.dto.NoticeRequest;
import com.teumteum.admin.dto.NoticeResponse;
import com.teumteum.admin.dto.PageResponse;
import com.teumteum.admin.exception.AdminErrorCode;
import com.teumteum.admin.repository.NoticeRepository;
import com.teumteum.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * [이 파일이 하는 일]
 * 공지사항 (REQ-049): 관리자는 등록·수정·삭제, 누구나 목록·상세 조회.
 * 전체 알림(REQ-053)은 별도 기능 없이 "가장 최근 공지 1건"(목록 size=1)을 홈 배너로 보여 주는 것으로 대신한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    static final int MAX_PAGE_SIZE = 50;

    private final NoticeRepository noticeRepository;

    public PageResponse<NoticeResponse> getNotices(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return PageResponse.of(noticeRepository.findAllByOrderByCreatedAtDescIdDesc(pageable), NoticeResponse::from);
    }

    public NoticeResponse getNotice(Long noticeId) {
        return NoticeResponse.from(find(noticeId));
    }

    /** 등록. adminMemberId = 등록한 관리자 (@LoginMember) */
    @Transactional
    public NoticeResponse create(Long adminMemberId, NoticeRequest request) {
        Notice saved = noticeRepository.save(Notice.create(adminMemberId, request.title().trim(), request.content()));
        return NoticeResponse.from(saved);
    }

    /** 수정. 관리자라면 누가 쓴 공지든 고칠 수 있다 */
    @Transactional
    public NoticeResponse update(Long noticeId, NoticeRequest request) {
        Notice notice = find(noticeId);
        notice.update(request.title().trim(), request.content());
        return NoticeResponse.from(notice);
    }

    @Transactional
    public void delete(Long noticeId) {
        noticeRepository.delete(find(noticeId));
    }

    private Notice find(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new CustomException(AdminErrorCode.NOTICE_NOT_FOUND));
    }
}
