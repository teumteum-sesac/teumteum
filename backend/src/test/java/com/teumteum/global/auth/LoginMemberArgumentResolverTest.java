package com.teumteum.global.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * [이 파일이 하는 일]
 * @LoginMember 가 헤더 X-Member-Id 를 회원 ID 로 넣어 주는지, 없거나 이상하면 401 을 주는지 확인한다.
 * 테스트용 컨트롤러(TestController)를 하나 만들어 실제 팀원 컨트롤러처럼 써 본다.
 *
 * 팀원이 자기 컨트롤러를 테스트할 때도 이렇게 하면 된다:
 *   MockMvcBuilders.standaloneSetup(new 내컨트롤러(...))
 *           .setCustomArgumentResolvers(new LoginMemberArgumentResolver())   // ← @LoginMember 쓰려면 이 줄 추가
 *           .setControllerAdvice(new GlobalExceptionHandler())
 *           .build();
 *   mockMvc.perform(post("/api/courses").header("X-Member-Id", "1") ...)
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.global.auth.*"
 */
class LoginMemberArgumentResolverTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("헤더 X-Member-Id 값이 @LoginMember 매개변수에 들어온다")
    void resolvesMemberId() throws Exception {
        mockMvc.perform(get("/test/me").header("X-Member-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7));
    }

    @Test
    @DisplayName("헤더가 없으면 401 UNAUTHORIZED")
    void noHeader() throws Exception {
        mockMvc.perform(get("/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요해요."));
    }

    @Test
    @DisplayName("숫자가 아니거나 0 이하면 401 UNAUTHORIZED")
    void invalidHeader() throws Exception {
        mockMvc.perform(get("/test/me").header("X-Member-Id", "abc"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/test/me").header("X-Member-Id", "0"))
                .andExpect(status().isUnauthorized());
    }

    /** 테스트용 컨트롤러: 받은 회원 ID 를 그대로 돌려준다 */
    @RestController
    static class TestController {
        @GetMapping("/test/me")
        ResponseEntity<ApiResponse<Long>> me(@LoginMember Long memberId) {
            return ResponseEntity.ok(ApiResponse.ok(memberId));
        }
    }
}
