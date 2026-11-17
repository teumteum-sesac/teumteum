package com.teumteum.global.config;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teumteum.course.controller.CourseController;
import com.teumteum.course.dto.CourseListResponse;
import com.teumteum.course.service.CourseService;
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
 * 보안 설정 전체(JwtAuthenticationFilter → SecurityConfig 경로 규칙 → @LoginMember)가 실제로 맞물려 동작하는지 확인한다.
 * 저장 코스 목록(GET /api/courses, 회원 전용)을 예로 쓴다.
 *   - 토큰 없음 / 위조 토큰 / 만료 토큰 → 401 공통 응답 (컨트롤러까지 가지 않음)
 *   - 진짜 토큰 → 200, 토큰 속 회원 ID 가 서비스로 전달됨
 *
 * [@WebMvcTest]
 *   DB 없이 웹 계층(컨트롤러·필터·예외 처리)만 띄우는 테스트. 서비스는 가짜(@MockitoBean)로 바꾼다.
 *   보안 설정·토큰 발급기는 직접 @Import 해서 실제 것을 쓴다.
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.global.config.SecurityConfigTest"
 */
@WebMvcTest(CourseController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-teumteum-jwt-0123456789",
        "jwt.access-token-validity-seconds=3600",
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private CourseService courseService;

    @Test
    @DisplayName("토큰 없이 회원 전용 API → 401 UNAUTHORIZED 공통 응답")
    void noToken() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요해요."));
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("진짜 토큰 → 200, 토큰 속 회원 ID 가 @LoginMember 로 들어간다")
    void validToken() throws Exception {
        given(courseService.getCourses(anyLong(), anyInt(), anyInt()))
                .willReturn(new CourseListResponse(List.of(), 0, 20, 0L, 0, false));
        String token = jwtProvider.createAccessToken(7L);

        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(courseService).getCourses(7L, 0, 20);
    }

    @Test
    @DisplayName("다른 비밀키로 만든 위조 토큰 → 401")
    void forgedToken() throws Exception {
        String forged = new JwtProvider("another-secret-key-for-forgery-0123456789", 3600).createAccessToken(7L);

        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("만료된 토큰 → 401")
    void expiredToken() throws Exception {
        String expired = new JwtProvider("test-secret-key-for-teumteum-jwt-0123456789", -1).createAccessToken(7L);

        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("예전 임시 헤더 X-Member-Id 만 보내면 → 401 (아무나 다른 회원 ID 를 넣을 수 있어서 삭제)")
    void oldHeaderRejected() throws Exception {
        mockMvc.perform(get("/api/courses").header("X-Member-Id", "7"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("회원 전용이 아닌 경로는 토큰이 없어도 막지 않는다 (없는 주소라 401 이 아닌 404)")
    void publicPathNotBlocked() throws Exception {
        mockMvc.perform(get("/api/places/not-exists"))
                .andExpect(status().isNotFound());
    }
}
