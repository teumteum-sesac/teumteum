package com.teumteum.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.teumteum.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;

// 이메일과 회원 ID는 받지 않는다. 수정 대상은 인증된 회원 본인이다.
public record MemberUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = Member.NICKNAME_MAX_LENGTH, message = "닉네임은 50자 이하로 입력해 주세요.")
        String nickname,
        // 두 비밀번호가 모두 null 또는 빈 문자열이면 기존 비밀번호를 유지한다.
        @Size(max = 64, message = "비밀번호는 64자 이하로 입력해 주세요.")
        String newPassword,
        @Size(max = 64, message = "비밀번호 확인은 64자 이하로 입력해 주세요.")
        String newPasswordConfirm
) {
    // @Valid가 필드 검증과 함께 호출한다. 원문 비밀번호를 trim하지 않는다.
    @Schema(hidden = true)
    @AssertTrue(message = "새 비밀번호는 8~64자·UTF-8 72바이트 이하로 입력하고 확인 값과 일치해야 해요.")
    public boolean isPasswordChangeValid() {
        boolean passwordEmpty = newPassword == null || newPassword.isEmpty();
        boolean confirmEmpty = newPasswordConfirm == null || newPasswordConfirm.isEmpty();
        if (passwordEmpty && confirmEmpty) return true;
        return !passwordEmpty && !confirmEmpty && !newPassword.isBlank()
                && newPassword.length() >= 8 && newPassword.length() <= 64
                // BCrypt가 처리할 수 있는 바이트 길이를 넘는 입력을 거절한다.
                && newPassword.getBytes(StandardCharsets.UTF_8).length <= 72
                && newPassword.equals(newPasswordConfirm);
    }
}
