package com.teumteum.member.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.member.domain.Member;
import com.teumteum.member.dto.SignupRequest;
import com.teumteum.member.dto.SignupResponse;
import com.teumteum.member.exception.MemberErrorCode;
import com.teumteum.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * [이 파일이 하는 일]
 * 회원 관련 "비즈니스 로직"을 담당한다. 지금은 회원가입과 이메일 중복 확인.
 *   Controller(요청 받기) → Service(판단·처리) → Repository(DB)
 *   Controller 는 요청을 넘기기만 하고, "중복이면 막는다", "비밀번호는 암호화한다" 같은 규칙은 전부 여기에 둔다.
 *
 * [누가 더 추가하나]
 *   - 로그인 (김용현, 다음 단계)
 *   - 회원 정보 조회·수정·탈퇴 (김하윤): 이 클래스에 메서드를 추가하거나 별도 Service 를 만든다
 *   다른 도메인(course, admin)은 MemberRepository 대신 이 Service 를 통해 회원을 다룬다 (AGENTS.md 5장)
 */
@Service                    // Spring 이 이 클래스를 Bean 으로 만들어 Controller 에 넣어 준다
@RequiredArgsConstructor    // final 필드를 받는 생성자를 Lombok 이 만들어 준다 → Spring 이 그 생성자로 아래 두 객체를 넣어 줌
@Transactional(readOnly = true)
// ↑ 이 클래스의 메서드는 기본적으로 "읽기 전용 트랜잭션"으로 실행한다 (조회 성능에 유리).
//   DB 를 바꾸는 메서드에만 따로 @Transactional 을 붙여 쓰기 가능하게 연다.
public class MemberService {

    // [의존성 주입] new MemberRepository() 하지 않는다. Spring 이 만들어 둔 객체를 생성자로 받아 쓴다.
    //   → 테스트할 때 가짜(Mock) 객체로 바꿔 끼우기 쉽다 (MemberServiceTest 참고)
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;   // global/config/PasswordEncoderConfig 에서 등록한 BCrypt

    /**
     * 회원가입 (REQ-001).
     *   1) 이메일 중복 확인 → 이미 있으면 DUPLICATE_EMAIL(409)
     *   2) 비밀번호를 BCrypt 해시로 변환 (NFR-001. 원문은 여기서 쓰고 버린다)
     *   3) 회원 저장 후 응답 DTO 로 변환
     *
     * @Transactional: 이 메서드 안의 DB 작업을 하나로 묶는다. 중간에 예외가 나면 전부 취소(rollback)된다.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        Member member = Member.create(request.email(), passwordHash, request.nickname());

        // save() 후 돌려받은 객체에는 DB 가 매긴 id(member_id)가 들어 있다
        Member saved = memberRepository.save(member);

        // TODO(김용현): 로그인(JWT) 구현 후 가입 직후 자동 로그인 — 토큰을 함께 돌려주기 (REQ-001)
        return SignupResponse.from(saved);
    }

    /**
     * 이메일이 이미 가입돼 있는지 확인 (회원가입 화면의 [중복 확인] 버튼).
     * 최종 판단은 signup() 에서 한 번 더 한다. 확인 버튼을 누른 뒤 가입 사이에 다른 사람이 같은 이메일로 가입할 수 있어서다.
     */
    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }
}
