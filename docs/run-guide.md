# 로컬 실행 가이드

> 담당: 김용현 · NFR-038 · 기준: 2026-11-19 `dev`
> 저장소를 처음 받은 팀원이 **DB → 백엔드 → 프론트**를 띄워서 화면이 실제 API와 연결되는 것까지 확인하는 순서입니다.
> 순서대로 하면 30분 안에 끝납니다. 막히면 맨 아래 [자주 나는 오류](#6-자주-나는-오류)를 먼저 보세요.

## 0. 필요한 프로그램

| 프로그램 | 버전 | 확인 명령 |
|---|---|---|
| JDK | 21 | `java -version` |
| Node.js | 20.19 이상 또는 22.12 이상 | `node -v` |
| MySQL | 8.0.16 이상 (CHECK 제약 사용) | MySQL Workbench 또는 `mysql --version` |
| Git | 아무 버전 | `git --version` |

## 1. DB 준비 (처음 한 번)

1. MySQL 서버가 켜져 있는지 확인 (Windows: 서비스 `MySQL80` 실행 중)
2. root 비밀번호를 확인한다. **MySQL 설치할 때 직접 정한 값**이다. 기억이 안 나면 [6.2](#62-mysql-비밀번호를-모름)
3. [ERD 문서 3. 전체 DDL](erd.md#3-전체-ddl) 을 통째로 복사해서 Workbench 에서 실행 → `teumteum` DB와 테이블 생성
4. 이어서 [4. 카테고리 기본 데이터](erd.md#4-카테고리-기본-데이터) 실행
5. 확인: `USE teumteum; SHOW TABLES;` 에 `member`, `course`, `course_place` … 가 보이면 끝

> 테이블은 JPA 가 자동으로 만들지 않습니다 (`ddl-auto` 설정 없음). 스키마가 바뀌면 ERD 문서 5절(전체 삭제) → 3 → 4 순서로 다시 만듭니다.

## 2. 설정 파일 만들기 (처음 한 번)

두 파일 모두 **git 에 올라가지 않는 파일**입니다. 키·비밀번호를 커밋하거나 단톡에 그대로 올리지 않습니다 (NFR-005).

### 2.1 백엔드 `backend/src/main/resources/application-local.yml`
`application-local.yml.example` 을 복사해서 이름을 바꾸고 값을 채운다.

| 설정 | 필수 | 없으면 | 어디서 받나 |
|---|---|---|---|
| `spring.datasource.password` | ✅ | 서버가 안 켜짐 (DB 접속 실패) | 본인 MySQL root 비밀번호 |
| `jwt.secret` | ✅ | 서버가 안 켜짐 | **32자 이상 아무 문자열** (각자 달라도 됨) |
| `jwt.access-token-validity-seconds` | ✅ | 서버가 안 켜짐 | `3600` 그대로 |
| `kakao.rest-api-key` | ✅ | 서버가 안 켜짐 (차량 이동시간 클라이언트가 필수로 읽음), 장소 검색 실패 | 카카오 개발자 콘솔 → 앱 → **REST API 키** (팀 공용 키는 김용현에게 개별 요청) |
| `ors.api-key` | | 켜짐. 도보 이동시간을 직선거리로 계산("예상 시간") | <https://openrouteservice.org> 가입 → 대시보드 토큰 |
| `odsay.api-key` | | 켜짐. 대중교통 이동시간을 직선거리로 계산 | <https://lab.odsay.com> 가입 → 애플리케이션 등록 (사용할 서버 IP·URL 등록 필요) |
| `tour.service-key` | | 켜짐. 장소 상세 API(`/api/places/detail`)의 사진·소개·운영시간이 비어 옴 (화면은 이 API 를 쓰지 않아 영향 없음) | 공공데이터포털 TourAPI → 디코딩 키 |

### 2.2 프론트 `frontend/.env.local`
`frontend/.env.example` 을 복사해서 이름을 바꾸고 값을 채운다.

| 설정 | 필수 | 없으면 | 어디서 받나 |
|---|---|---|---|
| `VITE_KAKAO_MAP_APP_KEY` | ✅ | 지도가 안 그려짐 | 카카오 개발자 콘솔 → 앱 → **JavaScript 키** (REST 키 아님!) · 플랫폼 Web 에 `http://localhost:5173` 등록 |

> 프론트에는 지도용 **JavaScript 키만** 둡니다. `VITE_` 값은 빌드 결과에 그대로 노출되기 때문입니다.

## 3. 백엔드 실행

```bash
cd backend
gradlew.bat bootRun          # Mac/Linux: ./gradlew bootRun
```
- 로그 끝에 `Started TeumteumApplication` 이 보이면 성공. 포트 **8080**
- 확인: <http://localhost:8080/swagger-ui/index.html> 이 열리면 정상
- 테스트만 돌릴 때: `gradlew.bat test` (DB 없이 돈다)

## 4. 프론트 실행

```bash
cd frontend
npm install                  # 처음 한 번, package.json 이 바뀌었을 때
npm run dev
```
- <http://localhost:5173> 접속. **`127.0.0.1` 이 아니라 `localhost`** 로 열어야 카카오 지도가 뜬다
- 프론트의 `/api` 요청은 `vite.config.ts` 프록시가 백엔드 8080 으로 넘긴다 (백엔드가 꺼져 있으면 화면에 "서버에 연결하지 못했어요")

## 5. 연결 확인 순서 (5분)

| 순서 | 화면 | 확인할 것 | 쓰는 API |
|---|---|---|---|
| 1 | `/signup` | 이메일 [중복 확인] → 가입 → 자동 로그인되어 홈으로 | `email-exists`, `POST /api/members` |
| 2 | `/mypage` | 닉네임·이메일, 저장한 코스 수 0 | `GET /api/courses?size=1` |
| 3 | 마이 → 로그아웃 → `/login` | 다시 로그인. 틀린 비밀번호면 "이메일 또는 비밀번호가 맞지 않아요." | `POST /api/auth/login` |
| 4 | `/` 홈 검색창 | "성수역" 검색 → 결과 목록 | `GET /api/places/search` |
| 5 | `/nearby` | 주변 장소 카드·지도 마커 | `GET /api/places/nearby` |
| 6 | `/condition` → [추천받기] | 출발지·도착지가 현재 위치로 채워짐 → 토끼 로딩 → 추천 코스 3개 | `POST /api/recommendations` |
| 7 | 코스 상세 → [이 코스 저장하기] → `/mypage/courses` | 저장한 코스 목록에 방금 코스 | `POST /api/courses`, `GET /api/courses` |

### 관리자 계정 만들기 (관리자 화면 `/admin` 시연용)
회원가입한 계정의 `member_id` 를 `admin` 테이블에 넣고 **다시 로그인**한다 (토큰에 관리자 표시가 들어가야 함).
```sql
INSERT INTO admin (member_id) SELECT member_id FROM member WHERE email = '관리자로 쓸 이메일';
```
관리자 계정은 본인 탈퇴가 막혀 있다. 관리자를 그만두려면 `admin` 행을 먼저 지운다.

Swagger 에서 회원 전용 API(🔒)를 시험할 때는 로그인 응답의 `accessToken` 을 **[Authorize]** 에 넣는다. 전체 API 목록은 [api-spec.md](api-spec.md).

## 6. 자주 나는 오류

### 6.1 백엔드가 안 켜짐
| 로그에 보이는 말 | 원인 | 해결 |
|---|---|---|
| `Access denied for user 'root'@'localhost'` | DB 비밀번호가 틀리거나 비어 있음 | `application-local.yml` 의 `password` 확인 |
| `Unknown database 'teumteum'` | DB 를 안 만듦 | [1. DB 준비](#1-db-준비-처음-한-번) |
| `Could not resolve placeholder 'jwt.secret'` / `kakao.rest-api-key` | 설정값 빠짐 | [2.1](#21-백엔드-backendsrcmainresourcesapplication-localyml) 표의 필수 값 채우기 |
| `WeakKeyException` | `jwt.secret` 이 32자보다 짧음 | 32자 이상으로 |
| `Port 8080 was already in use` | 이미 켜진 백엔드가 있음 | 이전 터미널 종료, 또는 작업 관리자에서 해당 java 프로세스 종료 |
| 실행 중 `Table 'teumteum.xxx' doesn't exist` | 테이블이 없거나 옛 스키마 | ERD 5절 → 3절 → 4절 다시 실행 |

### 6.2 MySQL 비밀번호를 모름
1. 시작 메뉴 **MySQL 8.0 Command Line Client** 에서 기억나는 값으로 시도
2. **MySQL Workbench** 의 `Local instance` 연결이 비밀번호 없이 열리면(저장돼 있음) 아래로 새 비밀번호 지정
   ```sql
   ALTER USER 'root'@'localhost' IDENTIFIED BY '새비밀번호';
   ```
3. 둘 다 안 되면 MySQL 공식 문서 "Resetting the Root Password: Windows Systems" 순서로 초기화 (서비스 중지 → 초기화 파일로 재시작)

### 6.3 프론트
| 증상 | 원인 | 해결 |
|---|---|---|
| 지도 자리가 비어 있음 | JavaScript 키 없음, 또는 `127.0.0.1` 로 접속 | `.env.local` 확인, `localhost:5173` 으로 접속, 카카오 콘솔 Web 플랫폼에 도메인 등록 |
| 모든 화면에 "서버에 연결하지 못했어요" | 백엔드 꺼짐 | [3. 백엔드 실행](#3-백엔드-실행) |
| 예전 다른 프로젝트 화면이 뜸 | 같은 포트를 쓰던 사이트의 캐시·서비스 워커 | 시크릿 창으로 열기 |
| 로그인했는데 갑자기 로그아웃됨 | 토큰 만료(1시간) 또는 백엔드 `jwt.secret` 이 바뀜 | 다시 로그인 |

### 6.4 VS Code
- `import io.jsonwebtoken` 등에 빨간 줄: `Ctrl+Shift+P` → **Java: Reload Projects** (build.gradle 바뀐 뒤 한 번)
- `.env.local` 을 바꿨는데 반영 안 됨: `npm run dev` 를 껐다 다시 켜기

## 변경 이력
| 날짜 | 내용 |
|---|---|
| 2026-11-19 | `tour.service-key` 설정, 연결 확인 6·7 최신화, 관리자 계정 만들기 추가 |
| 2026-11-18 | 첫 작성 (DB·설정·실행·연결 확인·오류 모음) |
