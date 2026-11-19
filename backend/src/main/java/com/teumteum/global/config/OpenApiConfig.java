package com.teumteum.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/*
 * [이 파일이 하는 일]
 * Swagger UI(http://localhost:8080/swagger-ui/index.html) 첫 화면의 안내문, API 그룹 순서, [Authorize] 버튼을 정한다.
 *
 * [그룹(태그) 이름 규칙]
 *   컨트롤러에 @Tag(name = TAG_xxx) 로 아래 상수를 붙인다. 앞의 숫자가 화면에 보이는 순서다.
 *   새 도메인 컨트롤러를 만들면 여기에 상수를 하나 추가한다.
 *
 * [Authorize 버튼]
 *   1) POST /api/auth/login 으로 로그인 → 응답 data.accessToken 복사
 *   2) 오른쪽 위 [Authorize] → 토큰만 붙여넣기 (Bearer 는 자동으로 붙는다)
 *   3) 이후 모든 요청에 Authorization: Bearer {토큰} 헤더가 붙는다
 */
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "POST /api/auth/login 응답의 accessToken 을 그대로 붙여넣으세요 (Bearer 는 자동)")
@OpenAPIDefinition(
        info = @Info(
                title = "틈틈 API",
                version = "v1",
                description = """
                        위치·시간 기반 즉흥 외출 추천 서비스 **틈틈** 의 API 입니다.

                        **테스트하는 순서**
                        1. `1. 인증·회원` → **회원가입**(이미 있으면 **로그인**) 실행 → 응답의 `accessToken` 복사
                        2. 오른쪽 위 **Authorize** 🔓 버튼 → 토큰 붙여넣기 → Authorize
                        3. 자물쇠 🔒 가 있는 API(저장 코스)도 이제 호출 가능
                        4. 관리자 API 는 **관리자 계정**으로 로그인한 토큰이 필요 (DB: `INSERT INTO admin (member_id) VALUES (회원번호);` 후 다시 로그인)

                        **응답 모양 (모든 API 공통)**
                        - 성공: `{ "success": true, "data": { ... }, "error": null }`
                        - 실패: `{ "success": false, "data": null, "error": { "code": "...", "message": "..." } }`

                        **로그인 필요** 표시가 없는 API 는 비회원도 쓸 수 있습니다.
                        전체 목록·구현 상태는 저장소의 `docs/api-spec.md` 를 보세요.
                        """
        ),
        security = @SecurityRequirement(name = "bearerAuth"),
        tags = {
                @Tag(name = OpenApiConfig.TAG_AUTH, description = "회원가입·로그인 (로그아웃은 API 없이 프론트가 토큰 삭제)"),
                @Tag(name = OpenApiConfig.TAG_PLACE, description = "장소·주소 검색, 좌표→주소, 주변 장소 (카카오 로컬)"),
                @Tag(name = OpenApiConfig.TAG_RECOMMEND, description = "외출 조건으로 코스 추천, 다시 추천, 코스 상세"),
                @Tag(name = OpenApiConfig.TAG_COURSE, description = "🔒 로그인 필요. 내가 저장한 코스 목록·상세·삭제"),
                @Tag(name = OpenApiConfig.TAG_ADMIN, description = "🔒 관리자 전용 (admin 테이블에 있는 회원으로 로그인한 토큰). 회원 관리·공지 등록"),
                @Tag(name = OpenApiConfig.TAG_NOTICE, description = "공지사항 조회 (누구나). 가장 최근 공지는 홈 배너"),
        }
)
public class OpenApiConfig {

    public static final String TAG_AUTH = "1. 인증·회원";
    public static final String TAG_PLACE = "2. 장소";
    public static final String TAG_RECOMMEND = "3. 추천";
    public static final String TAG_COURSE = "4. 저장 코스";
    public static final String TAG_ADMIN = "5. 관리자";
    public static final String TAG_NOTICE = "6. 공지사항";
}
