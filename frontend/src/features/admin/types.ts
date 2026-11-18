/*
 * [이 파일이 하는 일]
 * 관리자·공지사항 API 의 요청·응답 모양. 이름과 필드는 백엔드 DTO 와 같게 맞춘다.
 *   backend/src/main/java/com/teumteum/admin/dto/
 */

/** 페이지 목록 (PageResponse.java) */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

/** 회원 상태: 정상 / 탈퇴 */
export type AccountStatus = 'ACTIVE' | 'WITHDRAWN'

/** 관리자 화면의 회원 1명 (AdminMemberResponse.java) */
export interface AdminMemberResponse {
  memberId: number
  email: string
  nickname: string
  accountStatus: AccountStatus
  admin: boolean
  /** "2026-11-18T10:27:50" */
  createdAt: string
}

/** 공지사항 등록·수정 요청 (NoticeRequest.java). 제목 최대 200자 */
export interface NoticeRequest {
  title: string
  content: string
}

/** 공지사항 1건 (NoticeResponse.java) */
export interface NoticeResponse {
  noticeId: number
  title: string
  content: string
  createdAt: string
  updatedAt: string
}
