# 화면–API 매핑 및 구현 현황표

- 기준: `dev` `5eebd9e` (#154 까지), 2026-11-18 확인
- 근거: 백엔드 컨트롤러 실제 엔드포인트, 프론트 `features/*/api` 호출과 화면 import, GitHub PR·이슈, [API 명세 취합본](api-spec.md), [화면설계서](틈틈%20화면설계서.md), [요구사항](requirements.md)
- 작성: 유지수. 화면 ID·경로는 [AGENTS.md 6장](../AGENTS.md) 기준

## 읽는 법

| 표시 | 뜻 |
|---|---|
| ✅ 실제 | 프론트가 실제 백엔드 API 로 동작 |
| 🟡 일부 | 화면 일부가 목업·임시 저장·준비 중 |
| ⬜ 미연결 | 백엔드 API 는 있는데 프론트가 아직 안 부름 |
| ❌ API 없음 | 백엔드 API 가 아직 없음 (dev 기준) |

- **BE**: dev 에 엔드포인트가 있는지 / **FE**: 화면이 실제로 그 API 를 부르는지
- **확인 방법**: `실행` = 11/18 로컬(백엔드+프론트)에서 화면으로 확인, `코드` = 코드·PR 로만 확인 (실행 검증 안 함)

## 1. 화면별 현황 (화면 14개 + 공지·관리자)

| 화면 | 사용 API | BE | FE | 상태 | 담당 (화면 / API 연결) | 관련 PR·이슈 | 확인 | 남은 작업 |
|---|---|---|---|---|---|---|---|---|
| **HOM-01** 홈 | 카카오맵 SDK, 위치, `GET /api/places/search`(검색창), `GET /api/notices`(공지 배너) | ✅ | ✅ | ✅ 실제 | 유지수(지도)·김용현(하단바) / 김용현 | #86 #104 #112 #132 (merged) | 실행(지도·위치 거부 안내) / 코드(검색창·배너) | 검색 결과 중 **장소**를 누르면 HOM-02 와 같은 문제 (아래 DET-01 장소 1곳) |
| **HOM-02** 주변 장소 탐색 | `GET /api/places/nearby` | ✅ | ✅ | 🟡 일부 | 유지수·정종일 / 유지수 | #54 #67 #84 #139 (merged) | 실행(목록·마커·반경·필터·오류 다시 시도·0건 안내) | **장소 카드 → 상세가 '추천 만료' 안내로 끝남** (장소 상세 API #150 대기) |
| **CON-01** 외출 조건 설정 | `POST /api/recommendations` (추천받기) | ✅ | ✅ | ✅ 실제 | 정종일 / 김용현 | #90 #122 #136 (merged) | 실행(조건 입력 → 추천) | — |
| **CON-02** 위치 설정 | `GET /api/places/search`, `GET /api/places/address`(현재 위치·지도 핀) | ✅ | ✅ | ✅ 실제 | 정종일 / 정종일(검색)·유지수(지도 선택) | #88 #99 #146 (merged), #145 (closed) | 실행(검색·지도에서 직접 선택·주소 실패 다시 시도) | — |
| **RES-01** 추천 결과 목록 | `POST /api/recommendations`, `POST /api/recommendations/{recommendationSetId}/regenerate` | ✅ | ✅ | ✅ 실제 | 나혜빈 / 김용현(#136 전체 연결) | #114 #121 #125 #136 (merged), 이슈 #152 OPEN | 실행(추천 3개 표시) / 코드(다시 추천·429·만료) | #152 세분류 필터 두 단계·버스·지하철 표시 (브랜치만 있고 커밋 없음, PR 없음). 사진(TourAPI)·KOPIS 미적용 |
| **DET-01** 추천 결과 상세 | `GET /api/recommendations/courses/{recommendationId}`, 저장 `POST /api/courses` | 상세 ✅ / 저장 ❌ | 상세 ✅ / 저장 임시 | 🟡 일부 | 나혜빈 / 김용현 | #121 #136 (merged), 이슈 #120 OPEN | 실행(상세 → 지도 이동) | **[저장하기]가 브라우저 메모리 임시 저장** (`useSavedCourseStore`) → MY-02(실제 API)에 안 나옴. 저장 API #120 필요 |
| **DET-01** 장소 1곳 상세 (`/results/place-{id}`) | 장소 상세 (TourAPI, URL 미정) | ❌ | 목업 | 🟡 목업 | 나혜빈 / 김용현 | 이슈 #150 OPEN | 실행(실제 장소 ID → 만료 안내) | `recommend/mock.ts` 목업 장소만 보임. 실제 카카오 장소는 추천 API 로 조회돼 `RECOMMENDATION_NOT_FOUND` → '추천 만료' 안내. #150 후 연결 |
| **DET-02** 코스 지도 (추천) | 추천 코스 상세 응답의 `routeLegs` | ✅ | ✅ | ✅ 실제 | 유지수 / 김용현(#136) | #66 #95 #104 #136 #154 (merged) | 실행(차량·도보 실제 경로, 대중교통 직선, 마커·말풍선·목록·돌아가기) | 도보 실제 경로는 `application-local.yml` 의 `ors.api-key` 필요 (없으면 직선 대체) |
| **DET-02** 코스 지도 (저장) | `GET /api/courses/{courseId}` 의 `routeLegs` | ✅ | ✅ | ✅ 실제 (데이터 없음) | 유지수 / 김용현 | #75 #110 (merged) | 코드 | 저장 API #120 이 없어 저장 코스가 생기지 않음 → 실행 검증 못 함 |
| **DET-03** 장소 교체 | `GET …/places/{placeId}/alternatives`, `POST …/replace` | ✅ | ✅ | ✅ 실제 | 나혜빈 / 김용현 | #126 #133 #136 (merged) | 코드 (#136 PR 에 실서버 확인 기록) | — |
| **MY-01** 마이페이지 | `GET /api/courses`(저장 건수), `GET /api/members/me` | ✅ | 건수 ✅ / 내 정보 ⬜ | 🟡 일부 | 김하윤 / 김용현 | #110 #148 (merged) | 코드 | 닉네임·이메일은 로그인 응답 값(`authStore`)으로 표시. `GET /api/members/me`(#148) 는 프론트에서 아직 안 부름 |
| **MY-02** 저장한 코스 목록 | `GET /api/courses`, `DELETE /api/courses/{courseId}` | ✅ | ✅ | ✅ 실제 (데이터 없음) | 김하윤 / 김하윤 | #57 #75 #89 #103 (merged) | 코드 | 저장 API #120 이 없어 목록이 항상 비어 있음 |
| **MY-03** 저장한 코스 상세 | `GET /api/courses/{courseId}`, `DELETE`, [정보 새로고침] `GET /api/courses/{courseId}/places/latest` | 상세·삭제 ✅ / 새로고침 ❌ | 상세·삭제 ✅ / 새로고침 준비 중 | 🟡 일부 | 김하윤 / 김용현 | #75 #89 #110 (merged) | 코드 | '정보 새로고침' API 없음 (이슈도 없음, [course-api.md 5](course-api.md#5-최신-장소-정보-별도-조회) 명세만) |
| **MY-04** 회원 정보 수정·탈퇴 | `PATCH /api/members/me`, `DELETE /api/members/me` | ❌ | 화면 상태만 변경 | 🟡 미연결 | 김하윤 / 김용현 (#127 이관) | 이슈 #153 #155 OPEN | 코드 | 수정·탈퇴 API 대기 (브랜치만 있고 커밋 없음). `ProfileEdit.tsx` 가 `authStore` 값만 바꿈 |
| **AUT-01** 로그인 | `POST /api/auth/login`, 비밀번호 찾기(미정) | 로그인 ✅ / 찾기 ❌ | 로그인 ✅ / 찾기 '준비 중' 알림 | 🟡 일부 | 김용현 / 김용현 | #59 #107 #109 (merged) | 코드 | 비밀번호 찾기(REQ-054, P1) API·이슈 없음 |
| **AUT-02** 회원가입 | `POST /api/members`, `GET /api/members/email-exists` | ✅ | ✅ | ✅ 실제 | 김용현 / 김용현 | #42 #109 (merged) | 코드 | — |
| 공지 상세 (`/notices/:noticeId`) | `GET /api/notices/{noticeId}` | ✅ | ✅ | ✅ 실제 | 김용현 (#127 이관) | #132 (merged) | 코드 | — |
| 관리자 (`/admin`) | `GET /api/admin/members`, `PATCH …/withdraw`, `GET /api/notices`, `POST·PATCH·DELETE /api/admin/notices` | ✅ | ✅ | ✅ 실제 | 김용현 (#127 이관) | #132 (merged) | 코드 | 전체 알림(notification) 기능 없음. [api-spec.md](api-spec.md) 표의 관리자 행이 아직 ⬜ 로 남아 있음 |

## 2. 백엔드 API 존재 vs 프론트 연결

dev 컨트롤러에 있는 엔드포인트 21개 + 명세만 있는 API.

| API | BE (dev) | FE 연결 | 쓰는 화면 |
|---|---|---|---|
| `POST /api/auth/login` | ✅ | ✅ | AUT-01 |
| `POST /api/members` | ✅ | ✅ | AUT-02 |
| `GET /api/members/email-exists` | ✅ | ✅ | AUT-02 |
| `GET /api/members/me` | ✅ (#148) | ⬜ 안 부름 | MY-01·MY-04 예정 |
| `PATCH /api/members/me` | ❌ (#153 OPEN) | ⬜ | MY-04 |
| `DELETE /api/members/me` | ❌ (#155 OPEN) | ⬜ | MY-04 |
| `GET /api/places/search` | ✅ | ✅ | HOM-01, CON-02 |
| `GET /api/places/address` | ✅ | ✅ | CON-02 (현재 위치·지도 핀) |
| `GET /api/places/nearby` | ✅ | ✅ | HOM-02 |
| 장소 상세 (URL 미정) | ❌ (#150 OPEN) | 목업 | DET-01 장소 1곳 |
| `POST /api/recommendations` | ✅ | ✅ | CON-01 → RES-01 |
| `POST /api/recommendations/{recommendationSetId}/regenerate` | ✅ | ✅ | RES-01 |
| `GET /api/recommendations/courses/{recommendationId}` | ✅ | ✅ | DET-01·DET-02·DET-03 |
| `GET …/courses/{recommendationId}/places/{placeId}/alternatives` | ✅ | ✅ | DET-03 |
| `POST …/courses/{recommendationId}/replace` | ✅ | ✅ | DET-03 |
| `POST /api/courses` | ❌ (#120 OPEN) | 임시 저장 | DET-01 [저장하기] |
| `GET /api/courses` | ✅ | ✅ | MY-02, MY-01 건수 |
| `GET /api/courses/{courseId}` | ✅ | ✅ | MY-03, DET-02(저장) |
| `DELETE /api/courses/{courseId}` | ✅ | ✅ | MY-02·MY-03 |
| `GET /api/courses/{courseId}/places/latest` | ❌ (이슈 없음) | 준비 중 | MY-03 [정보 새로고침] |
| `GET /api/notices`, `GET /api/notices/{noticeId}` | ✅ | ✅ | HOM-01 배너, 공지 상세, 관리자 |
| `GET /api/admin/members`, `PATCH /api/admin/members/{memberId}/withdraw` | ✅ | ✅ | 관리자 |
| `POST /api/admin/notices`, `PATCH·DELETE /api/admin/notices/{noticeId}` | ✅ | ✅ | 관리자 |
| 비밀번호 찾기 (메일) | ❌ (이슈 없음, P1) | '준비 중' 알림 | AUT-01 |

## 3. 진행 중인 작업 (2026-11-18 기준)

- **OPEN PR: 없음** (#154 까지 모두 merged)
- OPEN 이슈와 진행 상태. 아래 브랜치는 모두 **dev 보다 앞선 커밋이 없음** (브랜치만 만들어짐)

| 이슈 | 내용 | 담당 | 브랜치 | 막히는 화면 |
|---|---|---|---|---|
| #120 | 추천 코스 저장 API | 김하윤 | `be05/feature/120-course-save` | DET-01 저장, MY-02·MY-03, 저장 코스 지도, 시연 9~10단계 |
| #150 | 장소 상세 조회 API | 정종일 | `be02/feature/150-place-detail` | HOM-01·HOM-02 → DET-01 장소 1곳 |
| #152 | 추천 결과 세분류 필터·버스·지하철 표시 | 나혜빈 | `fe03/feature/152-recommend-result-filter` | RES-01 (개선) |
| #153 | 내 회원 정보 수정 API | 김하윤 | `be05/feature/153-member-update` | MY-04 |
| #155 | 회원 탈퇴 API | 김하윤 | `be05/feature/155-member-withdraw` | MY-04 |

## 4. 남은 목업·임시 코드

| 위치 | 내용 | 없앨 조건 |
|---|---|---|
| `features/recommend/mock.ts`, `hooks/useRecommendCourse.ts` | 장소 1곳 상세(`place-*`) 목업 | #150 장소 상세 API 연결 |
| `features/mypage/store.ts` (`useSavedCourseStore`) | DET-01 저장을 메모리에만 저장 | #120 저장 API 연결 |
| `features/mypage/components/ProfileEdit.tsx` | 화면 상태만 변경 | #153·#155 연결 |
| `features/mypage/components/SavedCourseDetail.tsx` | '정보 새로고침' 준비 중 | 최신 장소 정보 API 구현 |
| `features/auth/components/LoginForm.tsx` | 비밀번호 찾기 '준비 중' 알림 | REQ-054 (P1) |

## 5. 시연 영향 요약

- **실제 데이터로 끝까지 되는 흐름**: 홈 → 외출 조건(위치 검색·지도 선택) → 추천 결과 → 상세 → 코스 지도(차량·도보 실제 경로, 대중교통 직선) → 장소 교체 → 다시 추천, 주변 장소 탐색 목록·지도, 로그인·회원가입, 공지
- **아직 막히는 부분**
  - 코스 저장 → 저장한 코스 목록·상세·지도 (#120)
  - 주변 탐색·홈 검색에서 장소를 눌러 상세 보기 (#150)
  - 회원 정보 수정·탈퇴 (#153·#155)
- **실행 환경**: 도보 실제 경로는 `ors.api-key`, 대중교통 실제 이동시간은 `odsay.api-key` 가 `application-local.yml` 에 있어야 한다 (없으면 직선거리로 대체, `estimated: true`)

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-11-18 | 최초 작성 (dev `5eebd9e` 기준) |
