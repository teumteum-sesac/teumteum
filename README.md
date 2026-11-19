# 틈틈 (TeumTeum)

> 남는 시간, 어디로 가볼까요?

출발지·도착지, 쓸 수 있는 시간(1~6시간), 이동수단, 관심 카테고리를 고르면 **그 시간 안에 다녀올 수 있는 외출 코스**(장소 1~4곳 + 방문 순서 + 이동시간)를 3개 추천하는 위치·시간 기반 즉흥 외출 추천 서비스입니다.
모바일 우선 SPA이고, 추천·탐색·상세·지도는 로그인 없이 쓸 수 있습니다. 코스 저장과 마이페이지만 로그인이 필요합니다.

## 주요 기능

| 영역 | 기능 |
|---|---|
| 홈 · 주변 탐색 | 지도 위 장소·주소 검색(자동완성), 현재 위치 주변 장소(반경 500m·1km·3km, 대분류·세분류 필터, 마커 묶음), 장소 화면(카카오맵 장소 정보, 주변 탐색에서 열면 내 위치 → 장소 도보 경로) |
| 외출 조건 | 출발지·도착지(필수, 기본값 현재 위치 `현재 위치 : 건물명`), 시작 시각 + 이용 시간 1~6시간, 이동수단(기본 도보), 카테고리 랜덤 / 직접 선택(최대 3개) |
| 추천 | 코스 3개 추천(이용 시간에 따라 장소 1~4곳, 세분류별 체류시간 범위, 이동시간 API · 실패 시 직선거리 '예상 시간'), 다시 추천, 세분류 필터, 장소 교체 |
| 코스 상세 · 지도 | 방문 시각 타임라인, 대중교통 노선 표시, 카카오맵 길찾기, 코스 지도(S·순번·깃발 마커, 차량·도보·대중교통 경로) |
| 저장 · 마이페이지 | 코스 저장(저장 당시 정보 그대로 보관)·저장 취소, 저장한 코스 목록·상세·지도·삭제, 회원 정보 수정, 탈퇴(동의 + 비밀번호) |
| 회원 · 관리자 | 이메일·비밀번호 회원가입·로그인(JWT), 관리자 회원 관리(검색·강제 탈퇴), 공지사항(최신 공지 = 홈 배너) |

## 기술 스택

| 영역 | 스택 |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Data JPA, Spring Security + JWT, Spring Validation, springdoc-openapi 3 (Swagger), Gradle |
| DB | MySQL 8 |
| Frontend | Vite, React 19, TypeScript, React Router, Zustand, axios, Tailwind CSS v4, oxlint |
| 외부 API | 카카오 로컬·지도 JS SDK·모빌리티(차량), OpenRouteService(도보), ODsay(대중교통), TourAPI(장소 상세) |

## 팀

| 이름 | 담당 |
|---|---|
| 김용현 | 공통 기반·인증(JWT), 공통 레이아웃, 프론트 화면 API 연결, 관리자 기능, API 명세 취합, 로컬 시연 환경 |
| 정종일 | 장소(카카오 로컬 API), 외출 조건 설정, 공통 응답·예외 처리, Swagger |
| 나혜빈 | 추천 로직(체류·이동·총 소요시간), 추천 API, 추천 결과·결과 상세 |
| 유지수 | 카카오 지도·마커·경로, 화면설계서·화면-API 매핑표, 시연 시나리오 |
| 김하윤 | ERD·테이블 정의서, 코스 저장·조회·삭제, 회원 정보 조회·수정·탈퇴, 마이페이지 |

## 구조

```
teumteum/
├── backend/    # Spring Boot (도메인형 패키지: global, member, place, recommend, course, admin)
├── frontend/   # Vite + React + TypeScript + Tailwind (기능형 폴더: app, pages, features, shared)
└── docs/       # 요구사항, 화면설계서, API 명세, ERD, 실행 가이드, 시연 시나리오
```

규칙(폴더 구조·코딩·Git)은 [AGENTS.md](./AGENTS.md), 문서 목록은 [docs/README.md](docs/README.md) 를 참고하세요.

## 로컬 실행

> 처음 실행하는 사람은 **[로컬 실행 가이드](docs/run-guide.md)** 를 보세요 (DB 테이블 만들기, 설정값 표, 관리자 계정, 자주 나는 오류). 전체 API 목록은 [API 명세 취합본](docs/api-spec.md).

### 필요한 버전
- Java 21 (JDK), Node.js 20.19 이상 또는 22.12 이상, MySQL 8.0.16 이상

### Backend
1. MySQL에 `teumteum` DB와 테이블 생성 ([ERD 문서](docs/erd.md) 3·4절 SQL 실행)
2. `backend/src/main/resources/application-local.yml.example` 을 복사해 `application-local.yml` 로 만들고 값 입력
3. `backend/` 에서 `gradlew.bat bootRun` (Mac/Linux: `./gradlew bootRun`) → <http://localhost:8080/swagger-ui/index.html>

### Frontend
1. `frontend/.env.example` 을 복사해 `.env.local` 로 만들고 카카오 지도 JavaScript 키 입력
2. `frontend/` 에서 `npm install` → `npm run dev`
3. <http://localhost:5173> 접속 (`/api` 요청은 백엔드 8080으로 전달됨)

발표는 로컬 환경으로 시연합니다 (서버 배포는 이번 범위 제외, NFR-039 P2).

## 브랜치 전략

`main`(시연) ← `dev`(통합) ← `{작업코드}/{type}/{이슈번호}-{도메인}-{기능}` (예: `be01/feature/12-auth-login`)

작업코드는 영역(`be`/`fe`) + 팀원 번호(01 김용현 ~ 05 김하윤)입니다. 커밋·PR 제목도 `[be01] feat: 로그인 API 추가` 처럼 작업코드로 시작합니다.

이슈를 먼저 만들고, `dev` 에서 브랜치를 따서 작업한 뒤 `dev` 로 PR을 올립니다(Squash merge). `dev` → `main` 은 시연할 때 Merge commit 으로 합칩니다. 자세한 규칙은 [AGENTS.md 8장](./AGENTS.md#8-git-규칙)을 참고하세요.
