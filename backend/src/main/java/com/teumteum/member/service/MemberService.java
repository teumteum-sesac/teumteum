package com.teumteum.member.service;

import com.teumteum.admin.service.AdminService;
import com.teumteum.global.auth.JwtProvider;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.member.domain.Member;
import com.teumteum.member.dto.LoginRequest;
import com.teumteum.member.dto.LoginResponse;
import com.teumteum.member.dto.MemberResponse;
import com.teumteum.member.dto.MemberUpdateRequest;
import com.teumteum.member.dto.SignupRequest;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.repository.MemberRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * [이 파일이 하는 일]
 * 회원 관련 "비즈니스 로직"을 담당한다. 회원가입, 이메일 중복 확인, 로그인.
 *   Controller(요청 받기) → Service(판단·처리) → Repository(DB)
 *   Controller 는 요청을 넘기기만 하고, "중복이면 막는다", "비밀번호는 암호화한다" 같은 규칙은 전부 여기에 둔다.
 *
 * [누가 더 추가하나]
 *   - 회원 정보 조회·수정·탈퇴 (김하윤): 이 클래스에 메서드를 추가하거나 별도 Service 를 만든다
 *   다른 도메인(course, admin)은 MemberRepository 대신 이 Service 를 통해 회원을 다룬다 (AGENTS.md 5장)
 */
@Service                    // Spring 이 이 클래스를 Bean 으로 만들어 Controller 에 넣어 준다
@RequiredArgsConstructor    // final 필드를 받는 생성자를 Lombok 이 만들어 준다 → Spring 이 그 생성자로 아래 객체들을 넣어 줌
@Transactional(readOnly = true)
// ↑ 이 클래스의 메서드는 기본적으로 "읽기 전용 트랜잭션"으로 실행한다 (조회 성능에 유리).
//   DB 를 바꾸는 메서드에만 따로 @Transactional 을 붙여 쓰기 가능하게 연다.
public class MemberService {

    // [의존성 주입] new MemberRepository() 하지 않는다. Spring 이 만들어 둔 객체를 생성자로 받아 쓴다.
    //   → 테스트할 때 가짜(Mock) 객체로 바꿔 끼우기 쉽다 (MemberServiceTest 참고)
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;   // global/config/PasswordEncoderConfig 에서 등록한 BCrypt
    private final JwtProvider jwtProvider;           // global/auth/JwtProvider: 로그인 토큰 발급
    private final AdminService adminService;         // admin/service: 관리자인지 확인 (토큰에 관리자 표시)

    /**
     * 회원가입 (REQ-001). 가입 직후 바로 로그인된 상태가 되도록 토큰까지 발급한다 (화면설계서 AUT-02 "가입 후 자동 로그인").
     *   1) 이메일 중복 확인 → 이미 있으면 DUPLICATE_EMAIL(409)
     *   2) 비밀번호를 BCrypt 해시로 변환 (NFR-001. 원문은 여기서 쓰고 버린다)
     *   3) 회원 저장
     *   4) 로그인 토큰 발급 → 로그인과 같은 응답(LoginResponse)
     *
     * @Transactional: 이 메서드 안의 DB 작업을 하나로 묶는다. 중간에 예외가 나면 전부 취소(rollback)된다.
     */
    @Transactional
    public LoginResponse signup(SignupRequest request) {
        // 탈퇴 회원용 이메일(Member.withdraw)을 미리 선점하면 그 회원의 탈퇴가 UNIQUE 충돌로 막힌다.
        // MySQL 기본 비교는 대소문자를 구분하지 않으므로 소문자로 바꿔 확인한다.
        if (request.email().toLowerCase(java.util.Locale.ROOT).endsWith(Member.WITHDRAWN_EMAIL_DOMAIN)) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        Member member = Member.create(request.email(), passwordHash, request.nickname());

        // save() 후 돌려받은 객체에는 DB 가 매긴 id(member_id)가 들어 있다 → 그 id 로 토큰을 만든다
        Member saved = memberRepository.save(member);
        return issueToken(saved);
    }

    /**
     * 이메일이 이미 가입돼 있는지 확인 (회원가입 화면의 [중복 확인] 버튼).
     * 최종 판단은 signup() 에서 한 번 더 한다. 확인 버튼을 누른 뒤 가입 사이에 다른 사람이 같은 이메일로 가입할 수 있어서다.
     */
    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    /**
     * 로그인 (REQ-002).
     *   1) 이메일로 회원 찾기         → 없으면 LOGIN_FAILED(401)
     *   2) 비밀번호 비교 (BCrypt)     → 틀리면 LOGIN_FAILED(401)
     *      passwordEncoder.matches(입력한 원문, 저장된 해시): 저장된 해시 안의 솔트로 다시 해시해서 비교
     *   3) 탈퇴한 회원인지            → 탈퇴했으면 LOGIN_FAILED(401)
     *   4) 토큰 발급
     * 세 가지 실패를 모두 같은 오류로 응답한다 (MemberErrorCode.LOGIN_FAILED 설명 참고)
     */
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(MemberErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash()) || !member.isActive()) {
            throw new CustomException(MemberErrorCode.LOGIN_FAILED);
        }

        return issueToken(member);
    }

    /** 회원 ID 로 토큰을 만들어 로그인 응답으로 감싼다 (가입·로그인 공통) */
    private LoginResponse issueToken(Member member) {
        // 관리자면 토큰에 관리자 표시를 넣는다 → /api/admin/** 통과 (SecurityConfig)
        boolean admin = adminService.isAdmin(member.getId());
        String accessToken = jwtProvider.createAccessToken(member.getId(), admin);
        return LoginResponse.of(member, accessToken, jwtProvider.getValiditySeconds(), admin);
    }

    // ===== 관리자 회원 관리용 (REQ-045). 관리자 도메인은 MemberRepository 대신 이 메서드들을 쓴다 =====

    /** 회원 목록. keyword 가 비어 있으면 전체, 있으면 이메일·닉네임 검색 */
    public Page<Member> searchMembers(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return memberRepository.findAll(pageable);
        }
        String k = keyword.trim();
        return memberRepository.findByEmailContainingIgnoreCaseOrNicknameContainingIgnoreCase(k, k, pageable);
    }

    /** 회원 한 명. 없으면 empty */
    public Optional<Member> findMember(Long memberId) {
        return memberRepository.findById(memberId);
    }

    /** 호출자의 쓰기 트랜잭션이 끝날 때까지 회원 행을 잠근다. 코스 저장용 서비스 계약이다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockActiveMember(Long memberId) {
        memberRepository.findByIdForUpdate(memberId).filter(Member::isActive)
                .orElseThrow(() -> new CustomException(CommonErrorCode.UNAUTHORIZED));
    }

    /** JWT 서명이 맞아도 삭제·탈퇴한 회원이면 인증하지 않는다. */
    public boolean isActiveMember(Long memberId) {
        return memberId != null && memberId > 0
                && findMember(memberId).filter(Member::isActive).isPresent();
    }

    /** 닉네임·선택적인 새 비밀번호를 수정한다. 이메일은 변경하지 않는다. */
    @Transactional
    public MemberResponse updateMyInfo(Long memberId, MemberUpdateRequest request) {
        if (memberId == null || memberId <= 0) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        // Controller 이외의 호출에서도 검증하고 필드를 변경하기 전에 오류를 거절한다.
        if (request == null || request.nickname() == null || request.nickname().isBlank()
                || request.nickname().length() > Member.NICKNAME_MAX_LENGTH
                || !request.isPasswordChangeValid()) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }

        Member member = findMember(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new CustomException(CommonErrorCode.UNAUTHORIZED));

        // 암호화가 실패해도 닉네임이 먼저 바뀌지 않도록 해시를 준비한 뒤 변경한다.
        String passwordHash = null;
        if (request.newPassword() != null && !request.newPassword().isEmpty()) {
            passwordHash = passwordEncoder.encode(request.newPassword());
        }
        member.changeNickname(request.nickname());
        if (passwordHash != null) {
            member.changePassword(passwordHash);
        }

        // 조회한 엔티티는 JPA가 관리하므로 트랜잭션 커밋 시 변경 감지로 UPDATE된다.
        // 수정 결과에는 비밀번호·해시를 넣지 않는다.
        return new MemberResponse(member.getEmail(), member.getNickname());
    }

    /** 인증된 회원 본인의 이메일·닉네임을 조회한다. 없거나 탈퇴한 회원은 401로 거절한다. */
    public MemberResponse getMyInfo(Long memberId) {
        Optional<Member> member = findMember(memberId);

        // 조회된 회원이 없으면 여기서 예외를 던지고 메서드를 끝낸다.
        if (member.isEmpty()) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }

        // 위 검사를 통과했으므로 Optional에서 실제 회원을 꺼낼 수 있다.
        Member foundMember = member.get();

        if (!foundMember.isActive()) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }

        return new MemberResponse(
                foundMember.getEmail(),
                foundMember.getNickname()
        );
    }
}
