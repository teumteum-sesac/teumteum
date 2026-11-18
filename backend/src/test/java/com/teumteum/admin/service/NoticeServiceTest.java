package com.teumteum.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.teumteum.admin.domain.Notice;
import com.teumteum.admin.dto.NoticeRequest;
import com.teumteum.admin.dto.NoticeResponse;
import com.teumteum.admin.exception.AdminErrorCode;
import com.teumteum.admin.repository.NoticeRepository;
import com.teumteum.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/*
 * [이 파일이 하는 일]
 * 공지사항(REQ-049) 등록·수정·삭제 규칙 확인. DB 없이 Repository 는 가짜로.
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.admin.*"
 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeRepository);
    }

    @Test
    @DisplayName("등록: 제목 앞뒤 공백을 지우고, 등록한 관리자 ID 와 함께 저장한다")
    void create() {
        given(noticeRepository.save(any(Notice.class))).willAnswer(invocation -> invocation.getArgument(0));

        NoticeResponse result = noticeService.create(1L, new NoticeRequest("  오픈 안내  ", "본문"));

        assertThat(result.title()).isEqualTo("오픈 안내");
        assertThat(result.content()).isEqualTo("본문");
    }

    @Test
    @DisplayName("수정: 제목·본문이 바뀐다")
    void update() {
        Notice notice = Notice.create(1L, "옛 제목", "옛 본문");
        given(noticeRepository.findById(3L)).willReturn(Optional.of(notice));

        NoticeResponse result = noticeService.update(3L, new NoticeRequest("새 제목", "새 본문"));

        assertThat(result.title()).isEqualTo("새 제목");
        assertThat(notice.getContent()).isEqualTo("새 본문");
    }

    @Test
    @DisplayName("없는 공지를 수정·삭제·조회하면 NOTICE_NOT_FOUND")
    void notFound() {
        given(noticeRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.update(9L, new NoticeRequest("t", "c")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.NOTICE_NOT_FOUND);
        assertThatThrownBy(() -> noticeService.delete(9L))
                .extracting("errorCode").isEqualTo(AdminErrorCode.NOTICE_NOT_FOUND);
        assertThatThrownBy(() -> noticeService.getNotice(9L))
                .extracting("errorCode").isEqualTo(AdminErrorCode.NOTICE_NOT_FOUND);
        verify(noticeRepository, never()).delete(any());
    }
}
