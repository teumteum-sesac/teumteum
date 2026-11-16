package com.teumteum.member.domain;

/*
 * [이 파일이 하는 일]
 * 회원 계정의 상태를 나타내는 값 목록(enum).
 *   - ACTIVE    : 정상 회원. 로그인·코스 저장 등 모든 회원 기능 사용 가능
 *   - WITHDRAWN : 탈퇴한 회원. 로그인 불가
 *
 * [왜 탈퇴를 "행 삭제"가 아니라 상태값으로 처리하나] (ERD: soft delete)
 *   member 행을 DELETE 하면 그 회원을 가리키던 다른 데이터(추천 기록 등)의 외래 키가 깨진다.
 *   그래서 행은 남겨 두고 상태만 WITHDRAWN 으로 바꾼다.
 *   (저장한 코스는 탈퇴 즉시 삭제한다 — 화면설계서 MY-04. 그 처리는 회원 탈퇴 기능 담당(김하윤)이 한다)
 *
 * DB 의 member.account_status 컬럼에 "ACTIVE" / "WITHDRAWN" 글자 그대로 저장된다 (Member.java 참고).
 * 관리자 회원 관리(정종화)에서 정지 같은 상태가 필요해지면 여기에 값을 추가한다.
 */
public enum AccountStatus {
    ACTIVE,
    WITHDRAWN
}
