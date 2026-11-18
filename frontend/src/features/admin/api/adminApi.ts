import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/request'
import type { AdminMemberResponse, NoticeRequest, NoticeResponse, PageResponse } from '../types'

/*
 * [이 파일이 하는 일]
 * 관리자·공지사항 API 호출 함수.
 *   /admin/** : 관리자 토큰 필요 (아니면 403 FORBIDDEN "접근 권한이 없어요.")
 *   /notices  : 누구나 (홈 배너, 공지 상세)
 */

/** 회원 목록 (REQ-045). keyword 는 이메일·닉네임 검색 */
export function getAdminMembers(keyword: string, page = 0, size = 20) {
  return apiGet<PageResponse<AdminMemberResponse>>('/admin/members', { keyword: keyword || undefined, page, size })
}

/** 회원 강제 탈퇴. 본인·다른 관리자는 400, 이미 탈퇴면 409 */
export function withdrawMember(memberId: number) {
  return apiPatch<AdminMemberResponse>(`/admin/members/${memberId}/withdraw`)
}

/** 공지 목록 (누구나). 홈 배너는 size=1 */
export function getNotices(page = 0, size = 20) {
  return apiGet<PageResponse<NoticeResponse>>('/notices', { page, size })
}

/** 공지 상세 (누구나) */
export function getNotice(noticeId: number) {
  return apiGet<NoticeResponse>(`/notices/${noticeId}`)
}

/** 공지 등록·수정·삭제 (관리자, REQ-049) */
export function createNotice(body: NoticeRequest) {
  return apiPost<NoticeResponse>('/admin/notices', body)
}

export function updateNotice(noticeId: number, body: NoticeRequest) {
  return apiPatch<NoticeResponse>(`/admin/notices/${noticeId}`, body)
}

export function deleteNotice(noticeId: number) {
  return apiDelete<void>(`/admin/notices/${noticeId}`)
}
