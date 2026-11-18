package com.teumteum.member.controller;

import com.teumteum.global.auth.LoginMember;
import com.teumteum.global.config.OpenApiConfig;
import com.teumteum.member.dto.MemberResponse;
import com.teumteum.member.dto.MemberWithdrawRequest;
import com.teumteum.member.dto.MemberWithdrawResponse;
import com.teumteum.member.service.MemberWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.teumteum.global.common.ApiResponse;
import com.teumteum.member.dto.EmailExistsResponse;
import com.teumteum.member.dto.SignupRequest;
import com.teumteum.member.dto.LoginResponse;
import com.teumteum.member.service.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * [이 파일이 하는 일]
 * 회원 관련 HTTP 요청을 받는 입구. 요청을 받아 Service 에 넘기고, 결과를 공통 응답(ApiResponse)으로 감싸 돌려준다.
 * 판단·처리 로직은 두지 않는다 (MemberService 담당).
 *
 * [만든 API]
 *   POST /api/members                         회원가입 + 자동 로그인 토큰 발급 (REQ-001, 화면 AUT-02)
 *   (로그인은 AuthController: POST /api/auth/login)
 *   GET  /api/members/email-exists?email=...  이메일 중복 확인 (AUT-02 [중복 확인] 버튼)
 *
 * [URL 규칙] AGENTS.md 5장: /api/ + 복수형 명사. "회원(members)을 하나 만든다" = POST /api/members
 *
 * 서버 실행 후 Swagger UI(http://localhost:8080/swagger-ui/index.html)에서 직접 요청해 볼 수 있다.
 */
@Tag(name = OpenApiConfig.TAG_AUTH)
@RestController                   // 이 클래스의 메서드 반환값을 JSON 으로 바꿔 응답한다
@RequestMapping("/api/members")   // 아래 메서드들 주소 앞에 공통으로 붙는다
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberWithdrawalService memberWithdrawalService;

    @Operation(summary = "🔒 회원 탈퇴", description = "비밀번호 확인 또는 agreed=true로 탈퇴한다. 저장 코스·장소 스냅샷·경로를 삭제하고 회원을 탈퇴 상태로 변경한다. 성공 후 프론트는 토큰을 지우고 홈으로 이동한다.")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<MemberWithdrawResponse>> withdrawMyAccount(
            @Parameter(hidden = true) @LoginMember Long memberId,
            @Valid @RequestBody MemberWithdrawRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(memberWithdrawalService.withdrawMyAccount(memberId, request)));
    }

    /**
     * 회원가입.
     *   @Valid       : SignupRequest 의 검증 규칙(@Email, @Size …)을 먼저 검사. 틀리면 400 + fieldErrors (GlobalExceptionHandler)
     *   @RequestBody : 요청 본문 JSON → SignupRequest 객체로 변환
     *   응답 201 Created: "새로 만들어졌다"는 뜻의 상태 코드 (종일님 ApiResponse 사용 예시와 같은 방식)
     *   응답 data 는 로그인과 같은 LoginResponse (토큰 포함) → 프론트는 가입 직후 바로 로그인 상태가 된다
     */
    @Operation(summary = "회원가입", description = "가입하면 바로 로그인 상태가 되도록 응답에 accessToken 이 들어 있다 (201).\n\n비밀번호 8~64자, 닉네임 최대 50자. 실패: 409 DUPLICATE_EMAIL(이미 가입된 이메일), 400 INVALID_REQUEST(형식 오류)", security = {})
    @PostMapping
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody SignupRequest request) {
        LoginResponse response = memberService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * 이메일 중복 확인.
     *   @RequestParam : 주소 뒤 ?email=... 값을 받는다
     *   @NotBlank @Email : 쿼리 값도 검증. 틀리면 400 (GlobalExceptionHandler 의 HandlerMethodValidationException 처리)
     */
    @Operation(summary = "이메일 중복 확인", description = "exists=true 면 이미 가입된 이메일. 회원가입 화면 [중복 확인] 버튼", security = {})
    @GetMapping("/email-exists")
    public ResponseEntity<ApiResponse<EmailExistsResponse>> checkEmailExists(
            @Parameter(description = "확인할 이메일", example = "user@email.com") @RequestParam @NotBlank @Email String email
    ) {
        boolean exists = memberService.existsByEmail(email);
        return ResponseEntity.ok(ApiResponse.ok(new EmailExistsResponse(exists)));
    }

    @Operation(summary = "🔒 내 회원 정보 조회", description = "로그인한 회원의 이메일·닉네임을 반환한다. 인증이 없거나 회원이 없거나 탈퇴한 경우 401로 응답한다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyInfo(
            // 회원 ID는 요청 파라미터가 아니라 JWT로 인증된 회원 정보에서 가져온다.
            @Parameter(hidden = true) @LoginMember Long memberId
    ) {
        // 회원 조회와 탈퇴 여부 확인은 Service가 담당한다.
        MemberResponse response = memberService.getMyInfo(memberId);
        // HTTP 200과 공통 응답 형식으로 이메일·닉네임을 반환한다.
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
