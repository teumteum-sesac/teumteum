# 화면–API 매핑 및 구현 현황표

- 기준: `dev` `9b1eb6d` (#242 까지), 2026-11-19 확인
- 근거: 백엔드 컨트롤러 실제 엔드포인트, 프론트 `features/*/api` 호출과 화면 import, GitHub PR·이슈, [API 명세 취합본](api-spec.md), [화면설계서](틈틈%20화면설계서.md), [요구사항](requirements.md)
- 작성: 유지수. 화면 ID·경로는 [AGENTS.md 6장](../AGENTS.md) 기준

## 읽는 법

| 표시 | 뜻 |
|---|---|
| ✅ 실제 | 프론트가 실제 백엔드 API 로 동작 |
| 🟡 일부 | 화면 일부가 목업·임시 저장·준비 중 |
| ⬜ 미연결 | 백엔드 API 는 있는데 프론트가 안 부름 |
| ❌ API 없음 | 백엔드 API 가 아직 없음 (dev 기준) |

- **BE**: dev 에 엔드포인트가 있는지 / **FE**: 화면이 실제로 그 API 를 부르는지
- **확인 방법**: `실행` = 11/19 로컬(백엔드+프론트)에서 화면으로 확인, `코드` = 코드·PR 로만 확인 (실행 검증 안 함)

## 1. 화면별 현황 (화면 14개 + 장소 화면·공지·관리자)

| 화면 | 사용 API | BE | FE | 상태 | 담당 (화면 / API 연결) | 관련 PR | 확인 | 남은 작업 |
|---|---|---|---|---|---|---|---|---|
| **HOM-01** 홈 | 카카오맵 SDK, 위치, `GET /api/places/search`(검색 자동완성), `GET /api/notices`(공지 배너) | ✅ | ✅ | ✅ 실제 | 유지수(지도)·김용현(하단바) / 김용현 | #86 #104 #112 #132 #173 #224 | 실행(지도·위치·지도 버튼) / 코드(자동완성·배너) | — |
| **HOM-02** 주변 장소 탐색 | `GET /api/places/nearby`, `GET /api/places/search`(화면 내 검색) | ✅ | ✅ | ✅ 실제 | 유지수·정종일 / 유지수 | #54 #67 #84 #139 #203 #210 #224 | 실행(반경·클러스터·필터·검색 반경 내/밖·상세 보기) | 응답 약 1.2초 (백엔드가 카카오 검색을 차례로 호출). 속도 개선은 정종일 님과 협의 |
| 장소 화면 (`/places/:placeId`) | 카카오맵 SDK, 카카오맵 장소 페이지(바텀시트), `GET /api/routes/walk`(내 위치 → 장소 도보 경로, 주변 탐색에서 들어올 때만 #242) | ✅ | ✅ | ✅ 실제 | 김용현 / 김용현 | #180 #193 #208 #214 #224 #242 | 코드 | 새로고침으로 들어오면 넘겨받은 장소 정보가 없어 안내만 표시 (의도된 동작) |
| **CON-01** 외출 조건 설정 | `POST /api/recommendations` (추천받기) | ✅ | ✅ | ✅ 실제 | 정종일 / 김용현 | #90 #122 #136 #200 #201 #216 #230 #240 | 코드 | 출발지·도착지 기본값은 현재 위치(`GET /api/places/address` 로 주소 표시) |
| **CON-02** 위치 설정 | `GET /api/places/search`, `GET /api/places/address`(현재 위치·지도 핀) | ✅ | ✅ | ✅ 실제 | 정종일 / 정종일(검색)·유지수(지도 선택) | #88 #99 #146 #230 | 코드 | — |
| **RES-01** 추천 결과 목록 | `POST /api/recommendations`, `POST /api/recommendations/{recommendationSetId}/regenerate` | ✅ | ✅ | ✅ 실제 | 나혜빈 / 김용현 | #114 #121 #125 #136 #156 #182 #206 | 코드 | — |
| **DET-01** 추천 결과 상세 | `GET /api/recommendations/courses/{recommendationId}`, 저장 `POST /api/courses`, 저장 취소 `DELETE /api/courses/{courseId}` | ✅ | ✅ | ✅ 실제 | 나혜빈 / 김용현 | #121 #136 #160 #164 #208 #220 | 실행(비로그인 저장 → 로그인 → 자동 저장) | 카카오맵 길찾기는 도보·차량만 |
| **DET-02** 코스 지도 (추천) | 추천 코스 상세 응답의 `routeLegs` | ✅ | ✅ | ✅ 실제 | 유지수 / 김용현 | #66 #95 #104 #136 #154 #182 #207 #224 #236 | 코드 | 실제 경로는 `ors.api-key`(도보)·`odsay.api-key`(대중교통) 필요 (없으면 직선) |
| **DET-02** 코스 지도 (저장) | `GET /api/courses/{courseId}` 의 `routeLegs` | ✅ | ✅ | ✅ 실제 | 유지수 / 김용현 | #75 #110 #164 | 코드 | — |
| **DET-03** 장소 교체 | `GET …/places/{placeId}/alternatives`, `POST …/replace` | ✅ | ✅ | ✅ 실제 | 나혜빈 / 김용현 | #126 #133 #136 | 코드 | — |
| **MY-01** 마이페이지 | `GET /api/members/me`, `GET /api/courses`(저장 건수) | ✅ | ✅ | ✅ 실제 | 김하윤 / 김용현 | #110 #148 #164 #222 | 코드 | — |
| **MY-02** 저장한 코스 목록 | `GET /api/courses`, `DELETE /api/courses/{courseId}` | ✅ | ✅ | ✅ 실제 | 김하윤 / 김하윤 | #57 #75 #89 #103 #222 | 코드 | — |
| **MY-03** 저장한 코스 상세 | `GET /api/courses/{courseId}`, `DELETE /api/courses/{courseId}` | ✅ | ✅ | ✅ 실제 | 김하윤 / 김용현 | #75 #89 #110 #164 #214 #224 | 코드 | '정보 새로고침'(`GET …/places/latest`)은 #224 에서 화면에서 뺌. 최신 정보는 장소 화면의 카카오맵 정보로 대신 봄 |
| **MY-04** 회원 정보 수정·탈퇴 | `PATCH /api/members/me`, `DELETE /api/members/me` | ✅ | ✅ | ✅ 실제 | 김하윤 / 김용현 | #157 #158 #164 #212 | 코드 | 탈퇴는 현재 비밀번호 + 동의 체크가 모두 필요 (#212) |
| **AUT-01** 로그인 | `POST /api/auth/login`, 비밀번호 찾기(미정) | 로그인 ✅ / 찾기 ❌ | 로그인 ✅ / 찾기 '준비 중' 알림 | 🟡 일부 | 김용현 / 김용현 | #59 #107 #109 #202 | 코드 | 비밀번호 찾기(REQ-054, P1) API·이슈 없음 |
| **AUT-02** 회원가입 | `POST /api/members`, `GET /api/members/email-exists` | ✅ | ✅ | ✅ 실제 | 김용현 / 김용현 | #42 #109 | 코드 | — |
| 공지 상세 (`/notices/:noticeId`) | `GET /api/notices/{noticeId}` | ✅ | ✅ | ✅ 실제 | 김용현 | #132 | 코드 | — |
| 관리자 (`/admin`) | `GET /api/admin/members`, `PATCH …/withdraw`, `GET /api/notices`, `POST·PATCH·DELETE /api/admin/notices` | ✅ | ✅ | ✅ 실제 | 김용현 | #132 #190 #212 | 코드 | 전체 알림(notification) 기능 없음 |

## 2. 백엔드 API 존재 vs 프론트 연결

dev 컨트롤러에 있는 엔드포인트 28개 + 명세만 있는 API.

| API | BE (dev) | FE 연결 | 쓰는 화면 |
|---|---|---|---|
| `POST /api/auth/login` | ✅ | ✅ | AUT-01 |
| `POST /api/members` | ✅ | ✅ | AUT-02 |
| `GET /api/members/email-exists` | ✅ | ✅ | AUT-02 |
| `GET /api/members/me` | ✅ | ✅ | MY-01·MY-04 |
| `PATCH /api/members/me` | ✅ | ✅ | MY-04 |
| `DELETE /api/members/me` | ✅ | ✅ | MY-04 탈퇴 |
| `GET /api/places/search` | ✅ | ✅ | HOM-01, HOM-02, CON-02 |
| `GET /api/places/address` | ✅ | ✅ | CON-02 (현재 위치·지도 핀) |
| `GET /api/places/nearby` | ✅ | ✅ | HOM-02 |
| `GET /api/places/detail` (TourAPI 사진·소개·운영시간) | ✅ (#159) | ⬜ 안 부름 | 장소 화면이 #224 부터 카카오맵 정보로 대체 |
| `GET /api/routes/walk` | ✅ (#214) | ✅ | 장소 화면 도보 경로 |
| `POST /api/recommendations` | ✅ | ✅ | CON-01 → RES-01 |
| `POST /api/recommendations/{recommendationSetId}/regenerate` | ✅ | ✅ | RES-01 |
| `GET /api/recommendations/courses/{recommendationId}` | ✅ | ✅ | DET-01·DET-02·DET-03 |
| `GET …/courses/{recommendationId}/places/{placeId}/alternatives` | ✅ | ✅ | DET-03 |
| `POST …/courses/{recommendationId}/replace` | ✅ | ✅ | DET-03 |
| `POST /api/courses` | ✅ (#160) | ✅ | DET-01 저장 |
| `GET /api/courses` | ✅ | ✅ | MY-02, MY-01 건수 |
| `GET /api/courses/{courseId}` | ✅ | ✅ | MY-03, DET-02(저장) |
| `DELETE /api/courses/{courseId}` | ✅ | ✅ | MY-02·MY-03 삭제, DET-01 저장 취소 |
| `GET /api/courses/{courseId}/places/latest` | ✅ (#162) | ⬜ 안 부름 | MY-03 '정보 새로고침' (#224 에서 화면 제외) |
| `GET /api/notices`, `GET /api/notices/{noticeId}` | ✅ | ✅ | HOM-01 배너, 공지 상세, 관리자 |
| `GET /api/admin/members`, `PATCH /api/admin/members/{memberId}/withdraw` | ✅ | ✅ | 관리자 |
| `POST /api/admin/notices`, `PATCH·DELETE /api/admin/notices/{noticeId}` | ✅ | ✅ | 관리자 |
| 비밀번호 찾기 (메일) | ❌ (이슈 없음, P1) | '준비 중' 알림 | AUT-01 |

## 3. 진행 중인 작업 (2026-11-19 기준)

- **OPEN 이슈·PR**: 시연 시나리오 갱신(#225 · PR #226)만 열려 있음. 11/18 에 막혀 있던 #120 #150 #152 #153 #155 는 모두 끝남
- **결정이 필요한 것**
  - 프론트가 부르지 않는 API 2개(`/api/places/detail`, `/api/courses/{courseId}/places/latest`)를 남길지 정리할지 (정종일·김하윤)
  - 주변 장소 API 속도 개선 (정종일): `NearbyPlaceService.searchAll()` 이 카카오 검색 약 30번을 차례로 호출

## 4. 남은 목업·임시 코드

| 위치 | 내용 | 없앨 조건 |
|---|---|---|
| `features/auth/components/LoginForm.tsx` | 비밀번호 찾기 '준비 중' 알림 | REQ-054 (P1) |
| `shared/types/domain.ts` | 주변 탐색 카드 카테고리 아이콘이 임시 이모지 | 카테고리 스티커 에셋 적용 |

11/18 에 있던 장소 1곳 상세 목업(`recommend/mock.ts`), 저장 임시 저장(`useSavedCourseStore`), 회원 정보 수정 화면 상태만 변경은 모두 실제 API 로 바뀌었다.

## 5. 시연 영향 요약

- **실제 데이터로 끝까지 되는 흐름**: 홈 → 외출 조건(위치 검색·지도 선택) → 추천 결과 → 코스 지도 → 상세(카카오맵 길찾기) → 장소 교체 → 저장(비로그인이면 로그인 후 자동 저장) → 저장한 코스 목록·상세·지도 → 마이페이지, 주변 장소 탐색 → 장소 화면, 회원가입·회원 정보 수정·탈퇴, 공지, 관리자
- **시연에서 빼는 것**: 비밀번호 찾기(준비 중), 탈퇴·삭제(시연 데이터가 지워짐). 순서는 [시연 시나리오](시연-시나리오.md) 참고
- **실행 환경**
  - DB 에 [ERD](erd.md) 테이블 13개가 있어야 한다 (`course` 테이블이 없으면 저장이 실패)
  - 도보 실제 경로는 `ors.api-key`, 대중교통 실제 경로는 `odsay.api-key` 가 `application-local.yml` 에 있어야 한다 (없으면 직선거리로 대체, `estimated: true`)

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-11-18 | 최초 작성 (dev `5eebd9e` 기준) |
| 2026-11-19 | dev `995fabe` 기준으로 전면 갱신: 저장·회원 정보·장소 화면 연결 반영, 새 API(`/api/routes/walk`) 추가, 안 쓰는 API 표시 |
| 2026-11-19 | dev `9b1eb6d` 기준 보완: 외출 조건 도착지 필수·현재 위치 기본값·기본 도보·최근 검색 삭제(#230), 출발=도착 깃발만 표시(#236), 여유 시간 1시간 시작·직접 선택 문화생활 기본(#240), 장소 화면 도보 경로는 주변 탐색 진입 때만(#242). API 변경 없음 |
