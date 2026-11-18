package com.teumteum.global.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teumteum.admin.controller.AdminMemberController;
import com.teumteum.admin.dto.PageResponse;
import com.teumteum.admin.service.AdminMemberService;
import com.teumteum.member.service.MemberService;
import com.teumteum.global.auth.JwtProvider;
import com.teumteum.global.auth.SecurityErrorHandler;
import com.teumteum.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/*
 * [이 파일이 하는 일]
 * 관리자 API(/api/admin/**) 문 단속 확인 (REQ-044, NFR-004).
 *   토큰 없음 → 401 / 일반 회원 토큰 → 403 / 관리자 토큰 → 200
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.global.config.AdminSecurityTest"
 */
@WebMvcTest(AdminMemberController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-teumteum-jwt-0123456789",
        "jwt.access-token-validity-seconds=3600",
})
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AdminMemberService adminMemberService;
    @MockitoBean
    private MemberService memberService;

    @Test
    @DisplayName("토큰 없이 관리자 API → 401")
    void noToken() throws Exception {
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("일반 회원 토큰으로 관리자 API → 403 FORBIDDEN 공통 응답")
    void memberToken() throws Exception {
        given(memberService.isActiveMember(7L)).willReturn(true);
        String token = jwtProvider.createAccessToken(7L, false);

        mockMvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("접근 권한이 없어요."));
        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("관리자 토큰 → 200")
    void adminToken() throws Exception {
        given(memberService.isActiveMember(1L)).willReturn(true);
        given(adminMemberService.getMembers(any(), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));
        String token = jwtProvider.createAccessToken(1L, true);

        mockMvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
