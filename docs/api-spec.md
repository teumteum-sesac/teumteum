# 틈틈 API 명세 (취합본)

> 담당: 김용현 (취합) · NFR-032 · 기준: 2026-11-18 `dev`
> 전체 API를 한 장에서 찾는 문서입니다. 도메인별 상세 명세가 있으면 그 문서로 연결하고, 상세 문서가 없는 인증·회원·장소 API는 여기에 직접 적습니다.
> 실제 요청은 백엔드를 띄운 뒤 Swagger UI(<http://localhost:8080/swagger-ui/index.html>)에서 해 볼 수 있습니다.

## 1. 전체 API 목록

**로그인** 열: 🔓 누구나 / 🔒 로그인 토큰 필요 (`Authorization: Bearer {accessToken}`)
**상태** 열: ✅ 구현됨(dev) / ⬜ 명세만 있음

| 도메인 | 기능 | Method | URL | 로그인 | 상태 | 담당 | 화면 | 상세 |
|---|---|---|---|---|---|---|---|---|
| 인증 | 로그인 | POST | `/api/auth/login` | 🔓 | ✅ | 김용현 | AUT-01 | [2.1](#21-로그인) |
| 회원 | 회원가입 (+자동 로그인) | POST | `/api/members` | 🔓 | ✅ | 김용현 | AUT-02 | [2.2](#22-회원가입) |
| 회원 | 이메일 중복 확인 | GET | `/api/members/email-exists?email=` | 🔓 | ✅ | 김용현 | AUT-02 | [2.3](#23-이메일-중복-확인) |
| 회원 | 내 정보 조회·수정·탈퇴 | GET·PATCH·DELETE | `/api/members/me` | 🔒 | ⬜ | 김하윤 | MY-01·MY-04 | 명세 작성 전 |
| 장소 | 장소·주소 검색 | GET | `/api/places/search?query=&lat=&lng=` | 🔓 | ✅ | 정종일 | HOM-01, CON-02 | [3.1](#31-장소주소-검색) |
| 장소 | 좌표 → 주소 | GET | `/api/places/address?lat=&lng=` | 🔓 | ✅ | 정종일 | CON-02, 지도 핀 | [3.2](#32-좌표--주소) |
| 장소 | 주변 장소 | GET | `/api/places/nearby?lat=&lng=&categories=&radius=` | 🔓 | ✅ | 정종일 | HOM-02 | [3.3](#33-주변-장소) |
| 장소 | 장소 상세 (TourAPI) | GET | 미정 | 🔓 | ⬜ | 정종일 | DET-01(장소 1곳) | 명세 작성 전 |
| 추천 | 추천 생성 | POST | `/api/recommendations` | 🔓 | ✅ | 나혜빈 | CON-01 → RES-01 | [recommend-api.md 1](recommend-api.md#1-추천-생성) |
| 추천 | 다시 추천 | POST | `/api/recommendations/{recommendationSetId}/regenerate` | 🔓 | ✅ | 나혜빈 | RES-01 | [recommend-api.md 2](recommend-api.md#2-다시-추천-req-026) |
| 추천 | 추천 코스 상세 | GET | `/api/recommendations/courses/{recommendationId}` | 🔓 | ✅ | 나혜빈 | DET-01·DET-02 | [recommend-api.md 3](recommend-api.md#3-추천-코스-상세-det-01--det-02) |
| 추천 | 장소 교체 후보 | GET | `/api/recommendations/courses/{recommendationId}/places/{placeId}/alternatives` | 🔓 | ✅ | 나혜빈 | DET-03 | [recommend-api.md 4](recommend-api.md#4-장소-교체-후보-det-03) |
| 추천 | 장소 교체 | POST | `/api/recommendations/courses/{recommendationId}/replace` | 🔓 | ✅ | 나혜빈 | DET-03 | [recommend-api.md 5](recommend-api.md#5-장소-교체-req-029) |
| 코스 | 추천 코스 저장 | POST | `/api/courses` | 🔒 | ⬜ | 김하윤 | DET-01 [저장하기] | [course-api.md 1](course-api.md#1-추천-코스-저장) |
| 코스 | 내 저장 코스 목록 | GET | `/api/courses?page=0&size=20` | 🔒 | ✅ | 김하윤 | MY-02, MY-01 건수 | [course-api.md 2](course-api.md#2-내-저장-코스-목록-조회) |
| 코스 | 내 저장 코스 상세·지도 | GET | `/api/courses/{courseId}` | 🔒 | ✅ | 김하윤 | MY-03, DET-02 | [course-api.md 3](course-api.md#3-내-저장-코스-상세지도-조회) |
| 코스 | 내 저장 코스 삭제 | DELETE | `/api/courses/{courseId}` | 🔒 | ✅ | 김하윤 | MY-02·MY-03 | [course-api.md 4](course-api.md#4-내-저장-코스-삭제) |
| 코스 | 최신 장소 정보 조회 | GET | `/api/courses/{courseId}/places/latest` | 🔒 | ⬜ | 김하윤 | MY-03 [정보 새로고침] | [course-api.md 5](course-api.md#5-최신-장소-정보-별도-조회) |
| 관리자 | 회원 관리·공지·알림 | - | `/api/admin/**` | 🔒 | ⬜ | 정종화 | `/admin` | 명세 작성 전 |
| 인증 | 비밀번호 찾기 (메일) | - | 미정 | 🔓 | ⬜ | 김용현 | AUT-01 | P1, 명세 작성 전 |

로그아웃은 API가 없습니다. JWT는 서버가 로그인 상태를 저장하지 않으므로 프론트가 토큰을 지우면 로그아웃입니다 (REQ-003).

## 공통 규칙

### 주소
- 모든 API는 `/api/...`, 관리자 API는 `/api/admin/...` (AGENTS.md 5장)
- 프론트는 `/api` 를 빼고 부른다 (`shared/api/request.ts` 의 `apiGet('/places/search')` → `/api/places/search`)

### 공통 응답 (`ApiResponse`)
```json
// 성공
{ "success": true, "data": { ... }, "error": null }
// 실패
{ "success": false, "data": null,
  "error": { "code": "INVALID_REQUEST", "message": "입력값을 확인해 주세요.",
             "fieldErrors": [ { "field": "email", "message": "올바른 형식의 이메일 주소여야 합니다" } ] } }
```
- `fieldErrors` 는 입력값 검증 실패(400)일 때만 들어간다
- 프론트는 `message` 를 그대로 화면에 보여 준다 (사용자용 문장으로 작성)

### 인증 (NFR-002)
- 로그인·회원가입 응답의 `accessToken` 을 매 요청 헤더 `Authorization: Bearer {accessToken}` 로 보낸다 (프론트 `shared/api/client.ts` 가 자동으로 붙임)
- 토큰 유효 시간 1시간 (`jwt.access-token-validity-seconds`). 재발급 API 없음 → 만료되면 다시 로그인
- 🔒 경로(`/api/courses/**`, `/api/members/me/**`, `/api/admin/**`)는 토큰이 없거나 만료·위조면 401. 목록은 `global/config/SecurityConfig` 에 있고, 새 회원 전용 API를 만들면 거기에 추가한다
- 컨트롤러는 `@LoginMember Long memberId` 로 로그인 회원 ID를 받는다
- Swagger 에서 시험: `POST /api/auth/login` → `accessToken` 복사 → 오른쪽 위 **[Authorize]** 에 붙여넣기

### 공통 오류 코드 (`CommonErrorCode`)
| HTTP | code | message |
|---|---|---|
| 400 | `INVALID_REQUEST` | 입력값을 확인해 주세요. |
| 401 | `UNAUTHORIZED` | 로그인이 필요해요. |
| 403 | `FORBIDDEN` | 접근 권한이 없어요. |
| 404 | `NOT_FOUND` | 요청한 정보를 찾을 수 없어요. |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 요청이에요. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 요청 형식이에요. |
| 500 | `INTERNAL_SERVER_ERROR` | 일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요. |

도메인 오류 코드는 각 도메인 `exception/XxxErrorCode` 와 상세 명세를 본다.
프론트에서 서버에 연결하지 못하면(서버 꺼짐·시간 초과) `NETWORK_ERROR` 로 처리한다 (`shared/api/apiError.ts`).

## 2. 인증·회원 (김용현)

### 2.1 로그인
`POST /api/auth/login` · 🔓 · 화면 AUT-01 · REQ-002

요청 (`LoginRequest`)
```json
{ "email": "user@email.com", "password": "abc12345" }
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| email | ✅ | 빈 값 불가 |
| password | ✅ | 빈 값 불가 |

성공 200 (`LoginResponse`)
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600,
  "memberId": 1, "email": "user@email.com", "nickname": "틈틈이" }
```

실패
| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 이메일·비밀번호가 비어 있음 (`fieldErrors`) |
| 401 | `LOGIN_FAILED` | 없는 이메일·틀린 비밀번호·탈퇴 회원. 셋 다 같은 문구 "이메일 또는 비밀번호가 맞지 않아요." (어느 쪽이 틀렸는지 알려주지 않음) |

### 2.2 회원가입
`POST /api/members` · 🔓 · 화면 AUT-02 · REQ-001

요청 (`SignupRequest`)
```json
{ "email": "user@email.com", "password": "abc12345", "nickname": "틈틈이" }
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| email | ✅ | 이메일 형식, 최대 255자 |
| password | ✅ | 8 ~ 64자. BCrypt 해시로 저장 (NFR-001) |
| nickname | ✅ | 최대 50자 |

성공 **201** — 로그인과 같은 `LoginResponse` (토큰 포함 → 가입 즉시 로그인)

실패
| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 형식·길이 위반 (`fieldErrors`) |
| 409 | `DUPLICATE_EMAIL` | 이미 가입된 이메일 "이미 가입된 이메일이에요." |

### 2.3 이메일 중복 확인
`GET /api/members/email-exists?email=user@email.com` · 🔓 · 화면 AUT-02 [중복 확인]

성공 200 (`EmailExistsResponse`)
```json
{ "exists": true }
```
실패: 400 `INVALID_REQUEST` (빈 값·이메일 형식 아님)

## 3. 장소 (정종일)

### 3.1 장소·주소 검색
`GET /api/places/search` · 🔓 · 화면 HOM-01 검색창, CON-02 위치 설정 · REQ-007, REQ-052

| 파라미터 | 필수 | 규칙 |
|---|---|---|
| query | ✅ | 1 ~ 100자 |
| lat, lng | | 주면 결과마다 그 좌표 기준 `distance`(m) 가 온다 |

성공 200 — `PlaceSearchResult` 배열
```json
[ { "type": "PLACE", "kakaoPlaceId": "12345", "name": "성수역 2호선",
    "address": "서울 성동구 성수동2가 ...", "roadAddress": "서울 성동구 아차산로 100",
    "categoryGroupCode": "SW8", "categoryName": "교통,수송 > 지하철,전철 > 수도권2호선",
    "latitude": 37.5446, "longitude": 127.0559, "distance": 320 } ]
```
- `type`: `PLACE`(장소) / `ADDRESS`(주소). 주소 결과는 `kakaoPlaceId`·카테고리가 null

### 3.2 좌표 → 주소
`GET /api/places/address?lat=37.5446&lng=127.0559` · 🔓 · 현재 위치·지도 중앙 핀 표시

성공 200 (`AddressResponse`) — 못 찾으면 모든 값 null
```json
{ "displayName": "성수역 2호선", "roadAddress": "서울 성동구 아차산로 100", "jibunAddress": "서울 성동구 성수동2가 ..." }
```

### 3.3 주변 장소
`GET /api/places/nearby` · 🔓 · 화면 HOM-02 · REQ-015, REQ-051

| 파라미터 | 필수 | 규칙 |
|---|---|---|
| lat, lng | ✅ | 위도 -90 ~ 90, 경도 -180 ~ 180 |
| categories | | 대분류 코드 여러 개 (`CAFE,FOOD`). 비우면 전체 |
| radius | | 미터, 기본 1000 |

성공 200 — `NearbyPlaceResult` 배열 (`kakaoPlaceId`, `name`, `category`, `subCategory`, `address`, `roadAddress`, `latitude`, `longitude`, `distance` …)

장소 오류 코드 (`PlaceErrorCode`)
| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_COORDINATE` / `INVALID_CATEGORY` / `INVALID_RADIUS` | 잘못된 좌표·카테고리·반경 |
| 502 | `KAKAO_API_ERROR` | 카카오 로컬 API 실패 |
| 503 | `KAKAO_API_RATE_LIMIT` | 카카오 호출 한도 초과 |

## 4. 추천 (나혜빈) · 코스 저장 (김하윤)

요청·응답·오류가 길어서 도메인 문서에 있습니다.
- 추천: [recommend-api.md](recommend-api.md) — 추천 생성(✅)·다시 추천(✅)·코스 상세(✅)·장소 교체 후보(✅)·장소 교체(✅)
- 저장 코스: [course-api.md](course-api.md) — 저장·목록(✅)·상세(✅)·삭제(✅)·최신 정보

대분류 코드 (요청·응답 공통): `CULTURE` 문화생활 · `CAFE` 카페/디저트 · `FOOD` 음식/맛집 · `NATURE` 자연/휴식 · `ENTERTAINMENT` 오락/체험 · `SHOPPING` 쇼핑/구경 · `SPORTS` 스포츠활동 · `TOURISM` 관광/명소
이동수단: `TRANSIT` 대중교통 · `WALK` 도보 · `CAR` 차량 / 카테고리 방식: `DIRECT` 직접 선택 · `RANDOM` 랜덤

## 변경 이력
| 날짜 | 내용 |
|---|---|
| 2026-11-18 | 첫 취합 (인증·회원·장소 상세, 추천·코스는 도메인 문서 연결, 구현 상태 표시) |
| 2026-11-18 | 추천 API 4개(다시 추천·코스 상세·장소 교체 후보·장소 교체) 구현 상태 ✅ 로 변경 (나혜빈) |
