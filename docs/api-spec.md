# 틈틈 API 명세 (취합본)

> 담당: 김용현 (취합) · NFR-032 · 기준: 2026-11-19 `dev`
> 전체 API를 한 장에서 찾는 문서입니다. 도메인별 상세 명세가 있으면 그 문서로 연결하고, 상세 문서가 없는 인증·회원·장소 API는 여기에 직접 적습니다.
> 실제 요청은 백엔드를 띄운 뒤 Swagger UI(<http://localhost:8080/swagger-ui/index.html>)에서 해 볼 수 있습니다.

## 1. 전체 API 목록

**로그인** 열: 🔓 누구나 / 🔒 로그인 토큰 필요 (`Authorization: Bearer {accessToken}`) / 🔒 관리자 = 관리자 계정 토큰만 (일반 회원은 403)
**상태** 열: ✅ 구현됨(dev) / ⬜ 명세만 있음

| 도메인 | 기능 | Method | URL | 로그인 | 상태 | 담당 | 화면 | 상세 |
|---|---|---|---|---|---|---|---|---|
| 인증 | 로그인 | POST | `/api/auth/login` | 🔓 | ✅ | 김용현 | AUT-01 | [2.1](#21-로그인) |
| 회원 | 회원가입 (+자동 로그인) | POST | `/api/members` | 🔓 | ✅ | 김용현 | AUT-02 | [2.2](#22-회원가입) |
| 회원 | 이메일 중복 확인 | GET | `/api/members/email-exists?email=` | 🔓 | ✅ | 김용현 | AUT-02 | [2.3](#23-이메일-중복-확인) |
| 회원 | 내 정보 조회 | GET | `/api/members/me` | 🔒 | ✅ | 김하윤 | MY-01·MY-04 | [2.4](#24-내-회원-정보-조회) |
| 회원 | 내 정보 수정 | PATCH | `/api/members/me` | 🔒 | ✅ | 김하윤 | MY-04 | [2.5](#25-내-회원-정보-수정) |
| 회원 | 회원 탈퇴 | DELETE | `/api/members/me` | 🔒 | ✅ | 김하윤 | MY-04 | [2.6](#26-회원-탈퇴) |
| 장소 | 장소·주소 검색 | GET | `/api/places/search?query=&lat=&lng=` | 🔓 | ✅ | 정종일 | HOM-01, CON-02 | [3.1](#31-장소주소-검색) |
| 장소 | 좌표 → 주소 | GET | `/api/places/address?lat=&lng=` | 🔓 | ✅ | 정종일 | CON-02, 지도 핀 | [3.2](#32-좌표--주소) |
| 장소 | 주변 장소 | GET | `/api/places/nearby?lat=&lng=&categories=&radius=` | 🔓 | ✅ | 정종일 | HOM-02 | [3.3](#33-주변-장소) |
| 장소 | 장소 상세 (TourAPI 사진·소개·운영시간) | GET | `/api/places/detail?kakaoPlaceId=&name=&lat=&lng=` | 🔓 | ✅ | 정종일 | (프론트 미사용: #224 부터 장소 화면은 카카오맵 장소 정보 시트) | Swagger 참고 |
| 경로 | 도보 경로 (내 위치 → 장소) | GET | `/api/routes/walk?fromLat=&fromLng=&toLat=&toLng=` | 🔓 | ✅ | 김용현 | 장소 화면 `/places/:placeId` (주변 탐색에서 열었을 때) | [3.4](#34-도보-경로) |
| 추천 | 추천 생성 | POST | `/api/recommendations` | 🔓 | ✅ | 나혜빈 | CON-01 → RES-01 | [recommend-api.md 1](recommend-api.md#1-추천-생성) |
| 추천 | 다시 추천 | POST | `/api/recommendations/{recommendationSetId}/regenerate` | 🔓 | ✅ | 나혜빈 | RES-01 | [recommend-api.md 2](recommend-api.md#2-다시-추천-req-026) |
| 추천 | 추천 코스 상세 | GET | `/api/recommendations/courses/{recommendationId}` | 🔓 | ✅ | 나혜빈 | DET-01·DET-02 | [recommend-api.md 3](recommend-api.md#3-추천-코스-상세-det-01--det-02) |
| 추천 | 장소 교체 후보 | GET | `/api/recommendations/courses/{recommendationId}/places/{placeId}/alternatives` | 🔓 | ✅ | 나혜빈 | DET-03 | [recommend-api.md 4](recommend-api.md#4-장소-교체-후보-det-03) |
| 추천 | 장소 교체 | POST | `/api/recommendations/courses/{recommendationId}/replace` | 🔓 | ✅ | 나혜빈 | DET-03 | [recommend-api.md 5](recommend-api.md#5-장소-교체-req-029) |
| 코스 | 추천 코스 저장 | POST | `/api/courses` | 🔒 | ✅ | 김하윤 | DET-01 [저장하기] | [course-api.md 1](course-api.md#1-추천-코스-저장) |
| 코스 | 내 저장 코스 목록 | GET | `/api/courses?page=0&size=20` | 🔒 | ✅ | 김하윤 | MY-02, MY-01 건수 | [course-api.md 2](course-api.md#2-내-저장-코스-목록-조회) |
| 코스 | 내 저장 코스 상세·지도 | GET | `/api/courses/{courseId}` | 🔒 | ✅ | 김하윤 | MY-03, DET-02 | [course-api.md 3](course-api.md#3-내-저장-코스-상세지도-조회) |
| 코스 | 내 저장 코스 삭제 | DELETE | `/api/courses/{courseId}` | 🔒 | ✅ | 김하윤 | MY-02·MY-03 | [course-api.md 4](course-api.md#4-내-저장-코스-삭제) |
| 코스 | 최신 장소 정보 조회 | GET | `/api/courses/{courseId}/places/latest` | 🔒 | ✅ | 김하윤 | (프론트 미사용: #224 에서 MY-03 '정보 새로고침' 화면 제외) | [course-api.md 5](course-api.md#5-최신-장소-정보-별도-조회) |
| 관리자 | 회원 목록 (검색) | GET | `/api/admin/members?keyword=&page=&size=` | 🔒 관리자 | ✅ | 김용현 | `/admin` | Swagger 참고 |
| 관리자 | 회원 강제 탈퇴 | PATCH | `/api/admin/members/{memberId}/withdraw` | 🔒 관리자 | ✅ | 김용현 | `/admin` | Swagger 참고 |
| 관리자 | 공지 등록 (최신 공지 = 전체 알림 배너) | POST | `/api/admin/notices` | 🔒 관리자 | ✅ | 김용현 | `/admin` | Swagger 참고 |
| 관리자 | 공지 수정 | PATCH | `/api/admin/notices/{noticeId}` | 🔒 관리자 | ✅ | 김용현 | `/admin` | Swagger 참고 |
| 관리자 | 공지 삭제 | DELETE | `/api/admin/notices/{noticeId}` | 🔒 관리자 | ✅ | 김용현 | `/admin` | Swagger 참고 |
| 공지 | 공지 목록 (홈 배너는 size=1) | GET | `/api/notices?page=&size=` | 🔓 | ✅ | 김용현 | HOM-01 배너 | Swagger 참고 |
| 공지 | 공지 상세 | GET | `/api/notices/{noticeId}` | 🔓 | ✅ | 김용현 | `/notices/:noticeId` | Swagger 참고 |
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

## 2. 인증·회원 (가입·로그인: 김용현 / 본인 정보 조회: 김하윤)

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
| 400 | `INVALID_REQUEST` | 형식·길이 위반 (`fieldErrors`), 탈퇴 회원용 도메인(`@teumteum.invalid`) 이메일 |
| 409 | `DUPLICATE_EMAIL` | 이미 가입된 이메일 "이미 가입된 이메일이에요." |

탈퇴한 회원의 이메일은 탈퇴 시 비식별화되므로 같은 이메일로 다시 가입할 수 있다 ([2.6](#26-회원-탈퇴)).

### 2.3 이메일 중복 확인
`GET /api/members/email-exists?email=user@email.com` · 🔓 · 화면 AUT-02 [중복 확인]

성공 200 (`EmailExistsResponse`)
```json
{ "exists": true }
```
실패: 400 `INVALID_REQUEST` (빈 값·이메일 형식 아님)

### 2.4 내 회원 정보 조회
`GET /api/members/me` · 🔒 · 화면 MY-01·MY-04 · REQ-004

요청 본문·쿼리 파라미터 없음. `Authorization: Bearer {accessToken}`으로 인증한 회원 ID를 사용하며 클라이언트가 조회할 회원 ID를 지정하지 않는다.

성공 200 (`MemberResponse`, 아래 JSON은 공통 응답의 `data`)
```json
{ "email": "user@email.com", "nickname": "틈틈이" }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| email | string | 가입 이메일 |
| nickname | string | 현재 회원 닉네임 |

비밀번호·비밀번호 해시·로그인 토큰은 응답에 포함하지 않는다. 수정·탈퇴 기능은 별도 API로 구현한다.

| HTTP | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰 없음·만료·위조, 회원이 없거나 탈퇴한 경우 |

### 2.5 내 회원 정보 수정
`PATCH /api/members/me` · 🔒 · 화면 MY-04 · REQ-005

요청 (`MemberUpdateRequest`). 수정 대상은 인증된 본인이다. 이메일·회원 ID는 수정 요청 필드가 아니다.
```json
{ "nickname": "새닉네임", "newPassword": "newpass123", "newPasswordConfirm": "newpass123" }
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| nickname | ✅ | 공백만 입력 불가, 최대 50자 |
| newPassword | | 변경 시 8~64자, 공백만 입력 불가, BCrypt 제한에 따라 UTF-8 72바이트 이하 |
| newPasswordConfirm | | 비밀번호 변경 시 필수이며 새 비밀번호와 일치 |

비밀번호 두 필드가 모두 생략·null·빈 문자열이면 기존 비밀번호를 유지한다. 한쪽만 입력하거나 값이 다르면 400으로 거절한다. 비밀번호는 공백 제거 없이 그대로 검증하고 BCrypt 해시로 저장한다.

닉네임만 변경하는 요청:
```json
{ "nickname": "새닉네임" }
```

성공 200 (`MemberResponse`, 공통 응답의 `data`):
```json
{ "email": "user@email.com", "nickname": "새닉네임" }
```

비밀번호·해시는 반환하지 않는다. 수정 후 기존 조회 API로 변경된 정보를 확인할 수 있다.

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 닉네임·비밀번호 검증 실패 (DTO 검증은 `fieldErrors` 포함) |
| 401 | `UNAUTHORIZED` | 인증 실패, 회원 없음·탈퇴 상태 |

### 2.6 회원 탈퇴
`DELETE /api/members/me` · 🔒 · 화면 MY-04 · REQ-006

요청 (`MemberWithdrawRequest`). JWT로 인증한 본인만 탈퇴한다. **동의 체크와 현재 비밀번호를 둘 다** 보내야 한다.
```json
{ "password": "abc12345", "agreed": true }
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| agreed | boolean | 필수 true (안내 확인·탈퇴 동의) |
| password | string | 필수. 현재 비밀번호, 최대 64자 |

둘 중 하나만 받으면 토큰이 남아 있는 기기에서 비밀번호 없이 계정이 삭제될 수 있어 둘 다 요구한다 (2026-11-19 변경, 이전에는 둘 중 하나).

성공 200 (`MemberWithdrawResponse`, 공통 응답의 `data`):
```json
{ "withdrawn": true }
```

- 저장 코스와 하위 장소 스냅샷·경로를 삭제하고 회원 상태를 WITHDRAWN으로 변경한다. 회원 행·공용 장소·다른 회원 데이터는 유지한다.
- 이메일은 `withdrawn+{memberId}@teumteum.invalid` 로 바꾼다. 같은 이메일로 다시 가입할 수 있고, 탈퇴한 사람의 이메일을 보관하지 않는다.
- 관리자 강제 탈퇴(`PATCH /api/admin/members/{memberId}/withdraw`)도 같은 처리(코스 삭제·이메일 비식별화)를 거친다.
- 관리자 계정은 본인 탈퇴할 수 없다. 관리자 해제(DB `admin` 행 삭제) 후 탈퇴한다.
- 모든 변경은 한 트랜잭션으로 처리하며 실패 시 전체 롤백한다. 하위 삭제는 기존 DB의 ON DELETE CASCADE를 사용한다.
- 탈퇴 후 로그인은 실패하며, JWT 인증 시 회원 상태를 확인하므로 기존 토큰도 회원 전용·관리자 API에 사용할 수 없다. 공개 API는 비회원으로 이용할 수 있다.
- 프론트는 성공 응답 확인 후 토큰·로그인 상태를 비우고 홈으로 이동한다.

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 동의 체크 또는 비밀번호 누락, 입력 길이 위반, 본문 누락 |
| 400 | `WITHDRAWAL_PASSWORD_MISMATCH` | 현재 비밀번호가 틀린 경우 |
| 400 | `ADMIN_WITHDRAWAL_NOT_ALLOWED` | 관리자 계정이 본인 탈퇴를 시도한 경우 |
| 401 | `UNAUTHORIZED` | 인증 실패, 없는 회원·이미 탈퇴한 회원 |

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

### 3.4 도보 경로
`GET /api/routes/walk?fromLat=37.5803&fromLng=127.0469&toLat=37.5812&toLng=127.0480` · 🔓 · 장소 화면 `/places/:placeId` (주변 탐색 [상세 보기]에서 열었을 때만)

추천이 쓰는 도보 이동시간 계산(`TravelTimeService.findRoute(WALK)`, OpenRouteService)을 그대로 부른다. 경로 API가 실패하면 직선거리로 추정한다.

| 파라미터 | 규칙 |
|---|---|
| fromLat, toLat | -90 ~ 90 |
| fromLng, toLng | -180 ~ 180 |

성공 200 (`WalkRouteResponse`)
```json
{ "travelMinutes": 6, "estimated": false, "path": [ { "latitude": 37.5803, "longitude": 127.0469 }, { "latitude": 37.5812, "longitude": 127.0480 } ] }
```
- `estimated: true` 면 직선 추정값이고 `path` 는 출발·도착 두 점(직선)이다
- 프론트는 5km 넘게 멀거나 위치 권한이 없으면 부르지 않고 장소 마커만 보여 준다

## 4. 추천 (나혜빈) · 코스 저장 (김하윤)

요청·응답·오류가 길어서 도메인 문서에 있습니다.
- 추천: [recommend-api.md](recommend-api.md) — 추천 생성(✅)·다시 추천(✅)·코스 상세(✅)·장소 교체 후보(✅)·장소 교체(✅)
- 저장 코스: [course-api.md](course-api.md) — 저장·목록(✅)·상세(✅)·삭제(✅)·최신 정보

대분류 코드 (요청·응답 공통): `CULTURE` 문화생활 · `CAFE` 카페/디저트 · `FOOD` 음식/맛집 · `NATURE` 자연/휴식 · `ENTERTAINMENT` 오락/체험 · `SHOPPING` 쇼핑/구경 · `SPORTS` 스포츠활동 · `TOURISM` 관광/명소
이동수단: `TRANSIT` 대중교통 · `WALK` 도보 · `CAR` 차량 / 카테고리 방식: `DIRECT` 직접 선택 · `RANDOM` 랜덤

## 변경 이력
| 날짜 | 내용 |
|---|---|
| 2026-11-19 | 도보 경로 API(`/api/routes/walk`, 3.4) 추가, 프론트가 부르지 않는 장소 상세·최신 장소 정보 API 표시 |
| 2026-11-19 | 회원 탈퇴: 동의 체크 + 비밀번호 모두 필수, 관리자 본인 탈퇴 차단, 탈퇴 시 이메일 비식별화(재가입 허용), 관리자 강제 탈퇴도 코스 삭제 |
| 2026-11-19 | 관리자(회원 관리·공지) API 5개, 공지 조회 API 2개, 장소 상세 API 주소·구현 상태 반영. 관리자 담당 김용현으로 변경 |
| 2026-11-18 | 내 회원 정보 수정(PATCH /api/members/me) 요청·응답·비밀번호 유지 정책 추가 (#153) |
| 2026-11-18 | 회원 탈퇴 요청·응답·저장 코스 삭제·탈퇴 토큰 차단 정책 추가 (#155) |
| 2026-11-18 | 내 회원 정보 조회(GET /api/members/me) 응답·인증·오류 명세 추가 (#142) |
| 2026-11-18 | 첫 취합 (인증·회원·장소 상세, 추천·코스는 도메인 문서 연결, 구현 상태 표시) |
| 2026-11-18 | 추천 API 4개(다시 추천·코스 상세·장소 교체 후보·장소 교체) 구현 상태 ✅ 로 변경 (나혜빈) |
