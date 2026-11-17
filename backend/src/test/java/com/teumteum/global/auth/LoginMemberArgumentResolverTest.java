package com.teumteum.global.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * [이 파일이 하는 일]
 * @LoginMember 가 토큰 인증 정보(principal = 회원 ID)를 회원 ID 로 넣어 주는지, 없거나 이상하면 401 을 주는지 확인한다.
 * 테스트용 컨트롤러(TestController)를 하나 만들어 실제 팀원 컨트롤러처럼 써 본다.
 *
 * 팀원이 자기 컨트롤러를 테스트할 때도 이렇게 하면 된다:
 *   MockMvcBuilders.standaloneSetup(new 내컨트롤러(...))
 *           .setCustomArgumentResolvers(new LoginMemberArgumentResolver())   // ← @LoginMember 쓰려면 이 줄 추가
 *           .setControllerAdvice(new GlobalExceptionHandler())
 *           .build();
 *   mockMvc.perform(post("/api/courses").principal(new UsernamePasswordAuthenticationToken(1L, null, List.of())) ...)
 *   (실제 서버에서는 JwtAuthenticationFilter 가 토큰을 검증해 이 인증 정보를 만든다)
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

    /** 토큰 검증이 끝나 회원 memberId 로 인증된 상태 (JwtAuthenticationFilter 가 만드는 것과 같은 모양) */
    private static UsernamePasswordAuthenticationToken loginAs(Object memberId) {
        return new UsernamePasswordAuthenticationToken(memberId, null, List.of());
    }

    @Test
    @DisplayName("인증 정보의 회원 ID 가 @LoginMember 매개변수에 들어온다")
    void resolvesMemberId() throws Exception {
        mockMvc.perform(get("/test/me").principal(loginAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7));
    }

    @Test
    @DisplayName("인증 정보가 없으면(로그인 안 함) 401 UNAUTHORIZED")
    void notLoggedIn() throws Exception {
        mockMvc.perform(get("/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요해요."));
    }

    @Test
    @DisplayName("예전 임시 헤더 X-Member-Id 는 더 이상 통하지 않는다 (아무나 다른 회원 ID 를 넣을 수 있어서 삭제)")
    void oldHeaderIgnored() throws Exception {
        mockMvc.perform(get("/test/me").header("X-Member-Id", "7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원 ID 가 숫자(Long)가 아니거나 0 이하면 401 UNAUTHORIZED")
    void invalidPrincipal() throws Exception {
        mockMvc.perform(get("/test/me").principal(loginAs("someone")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/test/me").principal(loginAs(0L)))
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
