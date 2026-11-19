package com.teumteum.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 회원 1명 = 이 클래스 객체 1개 = DB member 테이블의 행 1줄.
 * JPA 가 이 클래스를 보고 member 테이블과 연결(매핑)해 준다. 이런 클래스를 "엔티티(Entity)"라고 부른다.
 *
 * 기준: docs/erd.md 의 member 테이블
 *   member_id | email | password_hash | nickname | account_status | created_at | updated_at
 *
 * [누가 쓰나]
 *   - 회원가입·로그인 (김용현)        : create(), isActive(), getPasswordHash()
 *   - 회원 조회·수정·탈퇴 (김하윤)    : getXxx(), changeNickname(), changePassword(), withdraw()
 *   - 관리자 회원 관리 (정종화)        : getXxx(), getAccountStatus()
 *   다른 도메인은 이 엔티티를 직접 고치지 말고 member 의 service 를 거쳐 쓴다 (AGENTS.md 5장 도메인 경계).
 *
 * [setter 를 만들지 않은 이유]
 *   setEmail(), setNickname() 처럼 아무 데서나 값을 바꿀 수 있으면, 어디서 왜 바뀌었는지 추적이 어렵다.
 *   그래서 값은 "의미 있는 이름의 메서드"로만 바꾼다 → changeNickname(), withdraw() 처럼.
 */
@Entity                                  // 이 클래스는 DB 테이블과 연결되는 엔티티다
@Table(name = "member")                  // 연결할 테이블 이름
@Getter                                  // Lombok: getEmail() 같은 값 꺼내는 메서드를 자동으로 만들어 줌
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// ↑ JPA 는 DB 에서 읽은 값을 담을 때 "빈 생성자"가 꼭 필요하다.
//   다만 우리 코드에서 new Member() 로 빈 회원을 만들면 안 되니까 protected 로 막아 둔다.
//   새 회원은 아래 create() 로만 만든다.
public class Member {

    /** 이메일 최대 길이. ERD email VARCHAR(255) */
    public static final int EMAIL_MAX_LENGTH = 255;

    /** 닉네임 최대 길이. ERD nickname VARCHAR(50) */
    public static final int NICKNAME_MAX_LENGTH = 50;

    @Id                                                   // 기본 키(PK)
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // 번호는 DB 가 자동으로 1, 2, 3 … 매김 (MySQL AUTO_INCREMENT)
    @Column(name = "member_id")                           // 필드 이름은 id 지만 컬럼 이름은 member_id
    private Long id;

    /** 로그인 ID. 가입 후 수정 불가 (화면설계서 MY-04). 같은 이메일로 두 번 가입할 수 없다 (unique) */
    @Column(nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
    private String email;

    /**
     * 비밀번호를 BCrypt 로 바꾼 값 (NFR-001). 비밀번호 원문은 절대 저장하지 않는다.
     * 원문 → 해시 변환은 회원가입 service 에서 한다. 이 엔티티는 이미 변환된 값만 받는다.
     * 컬럼 이름: 스프링이 camelCase(passwordHash)를 snake_case(password_hash)로 자동 변환한다.
     */
    @Column(nullable = false)
    private String passwordHash;

    /** 화면에 보여 줄 이름 */
    @Column(nullable = false, length = NICKNAME_MAX_LENGTH)
    private String nickname;

    /**
     * 계정 상태. EnumType.STRING → DB 에 "ACTIVE" 같은 글자로 저장한다.
     * (ORDINAL 로 하면 0, 1 숫자로 저장되는데, 나중에 enum 순서가 바뀌면 기존 데이터의 뜻이 뒤바뀌어 위험하다)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus accountStatus;

    /** 가입 시각. 처음 저장될 때 Hibernate 가 현재 시각을 자동으로 넣는다. 이후 바뀌지 않음 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 마지막 수정 시각. 값이 바뀌어 저장될 때마다 Hibernate 가 자동으로 갱신한다 */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // private 생성자: 외부에서는 아래 create() 만 쓰게 한다
    private Member(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.accountStatus = AccountStatus.ACTIVE;   // 새 회원은 항상 정상 상태로 시작
    }

    /**
     * 새 회원 만들기 (회원가입).
     * new Member(...) 대신 이름이 있는 메서드로 만들어서 "회원가입으로 생긴 회원"이라는 뜻을 드러낸다.
     *
     * @param email        이메일 (중복 여부는 service 에서 먼저 확인한다)
     * @param passwordHash BCrypt 로 변환된 비밀번호 (원문 X)
     * @param nickname     닉네임
     */
    public static Member create(String email, String passwordHash, String nickname) {
        return new Member(email, passwordHash, nickname);
    }

    /** 닉네임 변경 (MY-04 회원 정보 수정) */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 비밀번호 변경. 역시 BCrypt 로 변환된 값을 받는다 (MY-04) */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** 탈퇴 회원의 이메일을 바꿔 둘 때 쓰는 도메인. .invalid 는 실제로 메일이 갈 수 없는 예약 도메인이다 */
    public static final String WITHDRAWN_EMAIL_DOMAIN = "@teumteum.invalid";

    /**
     * 탈퇴 처리. 행을 지우지 않고 상태만 바꾼다 (AccountStatus.java 설명 참고).
     * 이메일은 "withdrawn+{회원번호}@teumteum.invalid" 로 바꾼다.
     *   - email 은 UNIQUE 라서 그대로 두면 같은 이메일로 다시 가입할 수 없다
     *   - 탈퇴한 사람의 이메일(개인정보)을 계속 보관하지 않는다
     *   회원번호는 PK 라서 바꾼 이메일끼리도 겹치지 않는다.
     */
    public void withdraw() {
        this.accountStatus = AccountStatus.WITHDRAWN;
        this.email = "withdrawn+" + id + WITHDRAWN_EMAIL_DOMAIN;
    }

    /** 정상 회원인지. 로그인할 때 탈퇴한 회원을 걸러내는 데 쓴다 */
    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
