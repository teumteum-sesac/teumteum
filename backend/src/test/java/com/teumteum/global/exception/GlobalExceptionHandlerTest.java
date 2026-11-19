package com.teumteum.global.exception;

import com.teumteum.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 응답·예외 처리 테스트. DB·서버 없이 MockMvc 로 실행한다.
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.global.*"
 * <p>
 * 검증 예:
 * <pre>
 * mockMvc.perform(get("/test/custom"))
 *        .andExpect(status().isNotFound())
 *        .andExpect(jsonPath("$.error.code").value("TEST_NOT_FOUND"));
 * </pre>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 성공_응답은_data를_담고_error는_null이다() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("틈틈"))
                .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    void 데이터가_없는_성공_응답은_data가_null이다() throws Exception {
        mockMvc.perform(get("/test/empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    void CustomException은_ErrorCode의_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/custom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("TEST_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(TestErrorCode.TEST_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.error.fieldErrors").doesNotExist());
    }

    @Test
    void 요청_본문_검증_실패는_400과_필드별_오류를_응답한다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"count\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", containsInAnyOrder("name", "count")))
                .andExpect(jsonPath("$.error.fieldErrors[*].message",
                        containsInAnyOrder("이름을 입력해 주세요.", "1 이상이어야 해요.")));
    }

    @Test
    void 요청_파라미터_검증_실패는_400과_필드별_오류를_응답한다() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value("1 이상이어야 해요."));
    }

    @Test
    void 잘못된_JSON은_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{잘못된 json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors").doesNotExist());
    }

    @Test
    void 필수_파라미터_누락과_타입_오류는_400으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/test/param").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 지원하지_않는_메서드는_405로_응답한다() throws Exception {
        mockMvc.perform(post("/test/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 예상하지_못한_예외는_500과_공통_문구로_응답하고_내부_정보를_노출하지_않는다() throws Exception {
        String body = mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message")
                        .value(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("내부 메시지");
    }

    @Test
    void 본문과_파라미터_검증이_섞여도_본문_오류는_필드_이름으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/mixed").param("size", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"count\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
    }

    @Test
    void 서버_오류_CustomException은_5xx_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/custom-server"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("TEST_EXTERNAL_FAILED"));
    }

    @Test
    void JSON이_아닌_요청_형식은_415로_응답한다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void 따로_처리하지_않은_Spring_4xx_예외는_원래_상태를_유지한다() throws Exception {
        mockMvc.perform(get("/test/response-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 로그인하지_않은_사용자의_권한_오류는_401로_응답한다() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 로그인한_사용자의_권한_오류는_403으로_응답한다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("member", null, List.of()));
        try {
            mockMvc.perform(get("/test/denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ===== 테스트용 오류 코드·요청·컨트롤러 =====

    @Getter
    @RequiredArgsConstructor
    enum TestErrorCode implements ErrorCode {
        TEST_NOT_FOUND(HttpStatus.NOT_FOUND, "테스트 대상을 찾을 수 없어요."),
        TEST_EXTERNAL_FAILED(HttpStatus.BAD_GATEWAY, "외부 정보를 불러오지 못했어요.");

        private final HttpStatus status;
        private final String message;

        @Override
        public String getCode() {
            return name();
        }
    }

    record TestRequest(
            @NotBlank(message = "이름을 입력해 주세요.") String name,
            @Min(value = 1, message = "1 이상이어야 해요.") int count
    ) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/success")
        ResponseEntity<ApiResponse<String>> success() {
            return ResponseEntity.ok(ApiResponse.ok("틈틈"));
        }

        @GetMapping("/test/empty")
        ResponseEntity<ApiResponse<Void>> empty() {
            return ResponseEntity.ok(ApiResponse.ok());
        }

        @GetMapping("/test/custom")
        ResponseEntity<ApiResponse<Void>> custom() {
            throw new CustomException(TestErrorCode.TEST_NOT_FOUND);
        }

        @PostMapping("/test/valid")
        ResponseEntity<ApiResponse<TestRequest>> valid(@Valid @RequestBody TestRequest request) {
            return ResponseEntity.ok(ApiResponse.ok(request));
        }

        @GetMapping("/test/param")
        ResponseEntity<ApiResponse<Integer>> param(
                @RequestParam @Min(value = 1, message = "1 이상이어야 해요.") int size) {
            return ResponseEntity.ok(ApiResponse.ok(size));
        }

        @GetMapping("/test/unexpected")
        ResponseEntity<ApiResponse<Void>> unexpected() {
            throw new IllegalStateException("내부 메시지");
        }

        @PostMapping("/test/mixed")
        ResponseEntity<ApiResponse<Void>> mixed(
                @Valid @RequestBody TestRequest request,
                @RequestParam @Min(value = 1, message = "1 이상이어야 해요.") int size) {
            return ResponseEntity.ok(ApiResponse.ok());
        }

        @GetMapping("/test/custom-server")
        ResponseEntity<ApiResponse<Void>> customServer() {
            throw new CustomException(TestErrorCode.TEST_EXTERNAL_FAILED, new IllegalStateException("원인"));
        }

        @GetMapping("/test/response-status")
        ResponseEntity<ApiResponse<Void>> responseStatus() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @GetMapping("/test/denied")
        ResponseEntity<ApiResponse<Void>> denied() {
            throw new AccessDeniedException("권한 없음");
        }
    }
}
