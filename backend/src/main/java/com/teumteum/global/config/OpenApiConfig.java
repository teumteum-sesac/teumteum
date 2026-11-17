package com.teumteum.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/*
 * [이 파일이 하는 일]
 * Swagger UI(http://localhost:8080/swagger-ui/index.html)에 [Authorize] 버튼을 만든다.
 * 회원 전용 API(/api/courses 등)를 Swagger 에서 시험할 때:
 *   1) POST /api/auth/login 으로 로그인 → 응답의 data.accessToken 복사
 *   2) 오른쪽 위 [Authorize] → 토큰만 붙여넣기 (Bearer 는 자동으로 붙는다)
 *   3) 이후 모든 요청에 Authorization: Bearer {토큰} 헤더가 붙는다
 */
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@OpenAPIDefinition(security = @SecurityRequirement(name = "bearerAuth"))
public class OpenApiConfig {
}
