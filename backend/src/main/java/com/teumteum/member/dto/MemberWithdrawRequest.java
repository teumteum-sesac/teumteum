package com.teumteum.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

// 비밀번호 입력 또는 동의 체크 중 하나로 탈퇴 의사를 확인한다 (MY-04).
public record MemberWithdrawRequest(
        @Size(max = 64, message = "비밀번호는 64자 이하로 입력해 주세요.") String password,
        boolean agreed
) {
    @Schema(hidden = true)
    @AssertTrue(message = "탈퇴에 동의하거나 현재 비밀번호를 입력해 주세요.")
    public boolean isConfirmed() {
        return agreed || (password != null && !password.isBlank());
    }
}
