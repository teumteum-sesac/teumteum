# 추천 API 명세 (초안)

> 담당: 나혜빈 · 작성일: 2026-11-13 · 추천 생성·다시 추천·코스 상세·장소 교체 후보·장소 교체 5개 API의 초안입니다.
> 기준: [REQ-019 ~ 029, 050, 056](requirements.md#14-장소-및-코스-추천), 화면설계서 v2.0 RES-01 · DET-01 · DET-02 · DET-03.
> URL·DTO·오류 코드는 팀 확인 전 제안안입니다. 공통 응답(`ApiResponse`)·오류 형식은 [저장 코스 API 명세](course-api.md)의 공통 규칙과 맞춥니다.

## API 목록

| 기능 | Method | URL | 성공 상태 | 응답 데이터 DTO | 화면 |
|---|---|---|---|---|---|
| 추천 생성 | POST | `/api/recommendations` | 200 | `RecommendResponse` | CON-01 → RES-01 |
| 다시 추천 | POST | `/api/recommendations/{recommendationSetId}/regenerate` | 200 | `RecommendResponse` | RES-01 '다시 추천' |
| 추천 코스 상세 | GET | `/api/recommendations/courses/{recommendationId}` | 200 | `RecommendCourseDetailResponse` | DET-01, DET-02 |
| 장소 교체 후보 | GET | `/api/recommendations/courses/{recommendationId}/places/{placeId}/alternatives` | 200 | `AlternativePlaceListResponse` | DET-03 |
| 장소 교체 | POST | `/api/recommendations/courses/{recommendationId}/replace` | 200 | `RecommendCourseDetailResponse` | DET-03 '이 장소로 교체' |

## 공통 규칙

- **인증 없음.** 추천·상세·지도·교체는 비회원도 이용한다 (NFR-002). 로그인 상태면 토큰을 함께 보내도 되지만 결과는 같다.
- 실제 응답은 공통 `ApiResponse`로 감싼다. **이 문서의 성공 JSON은 공통 응답 안의 데이터 부분 예시**다.
- 오류 코드 이름은 공통 `ErrorCode` 반영 전 제안안이다. 외부 API 오류 원문·API 키는 응답에 넣지 않는다.
- 시간 길이는 **분 단위 정수**, 시각은 `HH:mm`, 만료 시각은 오프셋을 포함한 ISO 8601 문자열이다.
- 좌표 필드 이름은 [course-api.md](course-api.md)와 같이 `latitude`, `longitude` 를 쓴다. 프론트 `lat`/`lng` 와는 연동 시 맞춘다.
- 선택 정보가 없으면 `null`, 목록이 비면 `[]` 이다.

### ID 두 가지

| ID | 가리키는 것 | 예시 | 쓰는 곳 |
|---|---|---|---|
| `recommendationSetId` | 한 번의 추천 요청 결과 묶음 (코스 최대 3개 + 요청 조건) | `"rs_8f2a..."` | 다시 추천 |
| `recommendationId` | 묶음 안의 **코스 1개의 불변 버전**. 장소를 교체하면 새 ID가 생긴다 | `"rc_41b9..."` | 상세·지도·교체·**코스 저장**(course-api의 `recommendationId`) |

프론트 라우트 `/results/:courseId` 의 `courseId` 자리에 `recommendationId` 를 넣는다.

### 보관과 만료 (REQ-056)

- 서버는 추천 결과(조건·코스·교체 후보)를 **생성 시각부터 2시간** 보관한다. 응답의 `expiresAt` 으로 알려준다.
- 만료된 ID로 상세·교체에 들어오면 `410 RECOMMENDATION_EXPIRED` 를 반환한다. 프론트는 "추천이 만료되었습니다" 안내 후 같은 조건으로 추천 생성 API를 다시 호출한다.
- 보관 위치(DB 또는 서버 캐시)는 API 형식과 관계없으므로 구현 단계에서 정한다 (ERD 8장).

### 시간 계산 규칙

- `totalTravelMinutes = sum(places.travelMinutes) + finalTravelMinutes`
- `totalMinutes = sum(places.stayMinutes) + totalTravelMinutes` (REQ-023)
- `places[].travelMinutes` 는 **직전 지점 → 이 장소** 이동시간. 첫 장소는 출발지에서 온다.
- `finalTravelMinutes` 는 마지막 장소 → 도착지. 도착지가 없으면 `0` (돌아오는 시간은 계산하지 않음).
- 체류시간은 세분류별 예상 체류시간표(RES-01)를 쓴다. 세분류를 확인할 수 없으면 **기본 60분**.
- 이동시간 API가 실패해 직선거리로 계산한 구간이 하나라도 있으면 코스의 `estimated` 가 `true` 다 (NFR-025).

---

## 1. 추천 생성

### 1.1 기본 정보

| 항목 | 내용 |
|---|---|
| 설명 | 외출 조건으로 코스를 최대 3개 추천한다 |
| 요구사항 | REQ-019 ~ 025, 027, 028, 050 |
| Method · URL | `POST /api/recommendations` |
| 인증 | 없음 |
| 요청 DTO | `RecommendRequest` |
| 응답 DTO | `RecommendResponse` |

### 1.2 요청 — `RecommendRequest`

```json
{
  "origin": { "name": "성수역", "address": "서울 성동구 아차산로 100", "latitude": 37.5446, "longitude": 127.0559 },
  "destination": { "name": "서울숲", "address": null, "latitude": 37.5444, "longitude": 127.0374 },
  "startTime": "13:00",
  "availableMinutes": 180,
  "transportType": "TRANSIT",
  "categoryMode": "DIRECT",
  "categories": ["SPORTS", "SHOPPING"]
}
```

| 필드 | 타입 | 필수 | 설명 · 검증 |
|---|---|---|---|
| `origin` | LocationRequest | O | 출발지. 프론트 기본값은 현재 위치 |
| `destination` | LocationRequest 또는 null | X | 도착지. 없으면 출발지 주변에서 끝나는 코스 |
| `startTime` | string | O | `HH:mm`, 30분 단위 (`mm` 은 `00` 또는 `30`) |
| `availableMinutes` | integer | O | `0, 30, 60, 120, 180, 240, 300, 360` 중 하나. **0 = 시간 제한 없음** |
| `transportType` | string | O | `TRANSIT` · `WALK` · `CAR` |
| `categoryMode` | string | O | `DIRECT` · `RANDOM` |
| `categories` | string[] | X | 대분류 코드 0 ~ 3개, 중복 불가. `RANDOM` 이면 무시. `DIRECT` 인데 비어 있으면 `RANDOM` 으로 처리 |

`LocationRequest`: `name: string 또는 null`, `address: string 또는 null`, `latitude: number`(-90 ~ 90), `longitude: number`(-180 ~ 180).

대분류 코드: `CULTURE` `CAFE` `FOOD` `NATURE` `ENTERTAINMENT` `SHOPPING` `SPORTS` `TOURISM` (ERD 값 규칙과 동일).

### 1.3 성공 응답 — `RecommendResponse`

`200 OK`

```json
{
  "recommendationSetId": "rs_8f2a",
  "expiresAt": "2026-11-13T15:00:00+09:00",
  "condition": {
    "origin": { "name": "성수역", "address": "서울 성동구 아차산로 100", "latitude": 37.5446, "longitude": 127.0559 },
    "destination": { "name": "서울숲", "address": null, "latitude": 37.5444, "longitude": 127.0374 },
    "startTime": "13:00",
    "availableMinutes": 180,
    "transportType": "TRANSIT",
    "categoryMode": "DIRECT",
    "categories": ["SPORTS", "SHOPPING"]
  },
  "subCategoryFilters": ["당구", "볼링", "클라이밍", "소품샵", "팝업스토어", "시장", "백화점"],
  "courses": [
    {
      "recommendationId": "rc_41b9",
      "courseName": "당구장 A → 소품샵 B",
      "placeCount": 2,
      "startTime": "13:00",
      "totalMinutes": 160,
      "totalTravelMinutes": 30,
      "finalTravelMinutes": 7,
      "estimated": false,
      "places": [
        {
          "placeId": "101", "visitOrder": 1,
          "placeName": "당구장 A", "category": "SPORTS", "subCategory": "당구",
          "address": "서울 성동구 ○○로 1", "latitude": 37.5470, "longitude": 127.0500,
          "imageUrl": null, "openingHours": "10:00~24:00",
          "stayMinutes": 90, "travelMinutes": 15, "transitInfo": "버스 721 · 환승 0회"
        },
        {
          "placeId": "102", "visitOrder": 2,
          "placeName": "소품샵 B", "category": "SHOPPING", "subCategory": "소품샵",
          "address": "서울 성동구 ○○로 2", "latitude": 37.5460, "longitude": 127.0420,
          "imageUrl": null, "openingHours": null,
          "stayMinutes": 40, "travelMinutes": 8, "transitInfo": null
        }
      ]
    }
  ]
}
```

| `RecommendResponse` 필드 | 타입 | 설명 |
|---|---|---|
| `recommendationSetId` | string | 이번 추천 묶음 ID. 다시 추천에 사용 |
| `expiresAt` | string | 보관 만료 시각 (생성 + 2시간) |
| `condition` | RecommendConditionResponse | 서버가 실제로 적용한 조건. RES-01 조건 요약 칩과 '조건 바꾸기' 값 유지에 사용. `DIRECT` 인데 비어서 랜덤 처리했다면 `categoryMode` 는 `RANDOM` |
| `subCategoryFilters` | string[] | 세분류 필터 칩 목록 (REQ-050). `DIRECT` 면 고른 대분류의 세분류 전체, `RANDOM` 이면 결과 장소들의 세분류. '전체' 칩은 프론트가 붙인다 |
| `courses` | RecommendCourseResponse[] | 추천 코스 0 ~ 3개. **정렬 없음** (결정 3) |

| `RecommendCourseResponse` 필드 | 타입 | 설명 |
|---|---|---|
| `recommendationId` | string | 코스 1개의 버전 ID |
| `courseName` | string | 장소 이름을 `→` 로 이은 코스명 |
| `placeCount` | integer | 1 ~ 4 |
| `startTime` | string | 출발 시각 `HH:mm` |
| `totalMinutes` · `totalTravelMinutes` · `finalTravelMinutes` | integer | 위 시간 계산 규칙 참고 |
| `estimated` | boolean | 직선거리로 추정한 구간이 있으면 true ("예상 시간" 표시) |
| `places` | RecommendPlaceResponse[] | 방문 순서대로 정렬 |

| `RecommendPlaceResponse` 필드 | 타입 | 설명 |
|---|---|---|
| `placeId` | string | 장소 ID (장소 도메인 기준) |
| `visitOrder` | integer | 1부터 시작하는 방문 순서 |
| `placeName` | string | 장소명 |
| `category` · `subCategory` | string | 대분류 코드 · 세분류 이름 (예: `SPORTS`, `당구`) |
| `address` | string 또는 null | 주소 |
| `latitude` · `longitude` | number | 좌표 |
| `imageUrl` | string 또는 null | 썸네일 (TourAPI 사진, 전시·공연은 포스터). null이면 프론트가 카테고리 기본 이미지 |
| `openingHours` | string 또는 null | 운영시간 |
| `stayMinutes` | integer | 예상 체류시간(분) |
| `travelMinutes` | integer | 직전 지점 → 이 장소 이동시간(분) |
| `transitInfo` | string 또는 null | 대중교통이면 노선·환승 (예: `버스 721 · 환승 0회`) |

방문 시각(도착·출발)은 `startTime` 과 시간 값으로 프론트가 계산한다 (`buildSchedule`).

### 1.4 결과가 없을 때 (REQ-027)

조건에 맞는 장소가 없는 것은 **오류가 아니라 정상 처리 결과**이므로 `200` 과 빈 목록을 반환한다.

```json
{
  "recommendationSetId": "rs_9c01",
  "expiresAt": "2026-11-13T15:00:00+09:00",
  "condition": { "...": "요청 조건 그대로" },
  "subCategoryFilters": [],
  "courses": []
}
```

- 서버는 반경을 넓혀 다시 찾은 뒤에도 없을 때 빈 목록을 반환한다 (REQ-015, 반경 단계는 장소 담당과 확정).
- 프론트는 `courses` 가 비면 "조건에 맞는 코스가 없어요. 시간을 늘리거나 카테고리를 줄여보세요" 안내와 '조건 바꾸기' 버튼을 보여준다. 문구는 화면 담당이 정한다.
- 코스가 1 ~ 2개뿐이면 있는 만큼만 반환한다.

### 1.5 실패 응답

| HTTP 상태 | 제안 오류 코드 | 발생 상황 | 화면 처리 |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | 필수값 누락, 좌표 범위·시각 형식·이용 시간 단계·대분류 개수(4개 이상) 오류 | 조건 확인 안내 |
| 503 | `PLACE_SEARCH_UNAVAILABLE` | 장소 검색 외부 API가 응답하지 않아 후보를 하나도 얻지 못함 | "잠시 후 다시 시도해 주세요" |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 처리 실패 | 오류 안내, 재시도 가능 |

이동시간 API 실패는 오류로 응답하지 않는다. 직선거리로 계산하고 `estimated: true` 로 표시한다 (REQ-022).

### 1.6 서버 처리 순서

1. 요청 검증. `DIRECT` + 빈 `categories` 는 `RANDOM` 으로 바꾼다.
2. 이용 시간으로 장소 수 상한을 정한다: 0분 → 4곳, 60분 이하 → 1곳, 180분 이하 → 2곳, 300분 이하 → 3곳, 그 이상 → 4곳 (REQ-019).
3. 장소 도메인 서비스로 후보 장소를 받는다. 각 후보의 세분류로 체류시간을 정한다.
4. 상한 안에서 장소를 고르고 방문 순서를 정해 코스를 만든다. 이동시간은 고른 이동수단 API 하나만 호출한다 (대중교통 ODsay는 최종 후보 코스에만).
5. 총 소요시간이 이용 시간을 넘는 코스는 버린다. 0분이면 검증하지 않는다 (REQ-024).
6. 서로 다른 코스 최대 3개를 골라 2시간 보관하고 응답한다. 부족하면 반경을 넓혀 3 ~ 5를 반복한다.

---

## 2. 다시 추천 (REQ-026)

| 항목 | 내용 |
|---|---|
| Method · URL | `POST /api/recommendations/{recommendationSetId}/regenerate` |
| 요청 본문 | 없음 (서버에 보관한 조건을 그대로 사용) |
| 응답 | `200` · `RecommendResponse` (새 `recommendationSetId`) |

- 같은 조건으로 **다른 장소·조합**을 추천한다. 직전 묶음에 나온 장소는 가능한 한 제외한다. 랜덤 추천에도 같게 적용한다.
- 후보가 모자라면 겹치는 장소가 나올 수 있고, 그래도 없으면 1.4와 같은 빈 결과를 반환한다.

| HTTP 상태 | 제안 오류 코드 | 발생 상황 | 화면 처리 |
|---|---|---|---|
| 404 | `RECOMMENDATION_NOT_FOUND` | 없는 묶음 ID | 조건 설정으로 이동 |
| 410 | `RECOMMENDATION_EXPIRED` | 2시간이 지나 조건이 사라짐 | 프론트가 가진 조건으로 추천 생성 API 호출 |
| 429 | `TOO_MANY_REQUESTS` | 같은 사용자가 최근 1분 안에 다시 추천을 5번 넘게 요청 | "잠시 후 다시 시도해 주세요" |

- **다시 추천 제한:** 같은 사용자 기준 **최근 1분 동안 5번까지** 허용하고 6번째부터 `429` 를 반환한다. 다시 추천은 호출할 때마다 새 `recommendationSetId` 가 생기므로 묶음 ID가 아니라 사용자 기준으로 센다. 비회원도 이용하므로 사용자 구분 방법(IP 등)은 구현 단계에서 정한다.
- 외부 API 무료 한도를 지키기 위한 제한이다 (NFR-014).

---

## 3. 추천 코스 상세 (DET-01 · DET-02)

| 항목 | 내용 |
|---|---|
| Method · URL | `GET /api/recommendations/courses/{recommendationId}` |
| 응답 | `200` · `RecommendCourseDetailResponse` |

`RecommendCourseResponse` 의 모든 필드에 아래 필드를 더한다.

```json
{
  "recommendationId": "rc_41b9",
  "recommendationSetId": "rs_8f2a",
  "expiresAt": "2026-11-13T15:00:00+09:00",
  "origin": { "name": "성수역", "address": null, "latitude": 37.5446, "longitude": 127.0559 },
  "destination": { "name": "서울숲", "address": null, "latitude": 37.5444, "longitude": 127.0374 },
  "transportType": "TRANSIT",
  "availableMinutes": 180,
  "courseName": "당구장 A → 소품샵 B",
  "...": "RecommendCourseResponse 의 나머지 필드",
  "places": [
    { "...": "RecommendPlaceResponse 필드", "description": null, "kakaoMapUrl": "https://place.map.kakao.com/123" }
  ],
  "routeLegs": [
    { "fromVisitOrder": 0, "toVisitOrder": 1, "pathType": "STRAIGHT", "coordinates": [ { "latitude": 37.5446, "longitude": 127.0559 }, { "latitude": 37.5470, "longitude": 127.0500 } ] }
  ]
}
```

| 추가 필드 | 타입 | 설명 |
|---|---|---|
| `recommendationSetId` · `expiresAt` | string | 소속 묶음 ID · 만료 시각 |
| `origin` · `destination` | LocationResponse / null | 출발지 · 도착지 |
| `transportType` · `availableMinutes` | string · integer | 요청 조건. 교체 시 시간 초과 판단에 사용 |
| `places[].description` | string 또는 null | 소개 (펼침 영역) |
| `places[].kakaoMapUrl` | string 또는 null | 정보가 없는 장소의 '카카오맵에서 보기' 링크 |
| `routeLegs` | RouteLegResponse[] | 지도 경로. 형식은 [course-api.md 3.2](course-api.md) `CourseRouteLegResponse` 와 같다. 차량·도보는 `ACTUAL`(실제 경로), 대중교통과 직선 대체는 `STRAIGHT` |

- 이동시간은 추천할 때 계산해 보관한 값을 쓴다. 상세 조회에서 이동시간 API를 다시 부르지 않는다.
- 오류: `404 RECOMMENDATION_NOT_FOUND`, `410 RECOMMENDATION_EXPIRED`.
- 홈 검색·주변 탐색의 장소 1곳짜리 상세(`/results/place-{placeId}`)는 이 API 범위가 아니다. 장소 상세 API(정종일)와 연결 방식을 따로 정한다.

---

## 4. 장소 교체 후보 (DET-03)

| 항목 | 내용 |
|---|---|
| Method · URL | `GET /api/recommendations/courses/{recommendationId}/places/{placeId}/alternatives` |
| 응답 | `200` · `AlternativePlaceListResponse` |

```json
{
  "recommendationId": "rc_41b9",
  "currentPlace": { "placeId": "101", "visitOrder": 1, "placeName": "당구장 A", "category": "SPORTS", "subCategory": "당구", "stayMinutes": 90 },
  "alternatives": [
    {
      "placeId": "201", "placeName": "당구장 F", "category": "SPORTS", "subCategory": "당구",
      "address": "서울 성동구 ○○로 5", "latitude": 37.5455, "longitude": 127.0510,
      "imageUrl": null, "stayMinutes": 90,
      "travelMinutes": 12, "transitInfo": "버스 2014 · 환승 0회",
      "diffMinutes": -3, "exceedsAvailableTime": false
    }
  ]
}
```

| `AlternativePlaceResponse` 필드 | 타입 | 설명 |
|---|---|---|
| `placeId` ~ `stayMinutes` | | `RecommendPlaceResponse` 와 같은 의미 |
| `travelMinutes` · `transitInfo` | integer · string/null | 직전 지점 → 후보 장소 이동 정보 (화면의 "버스 12분") |
| `diffMinutes` | integer | 이 후보로 바꿨을 때 총 소요시간 변화. 음수면 줄어듦 |
| `exceedsAvailableTime` | boolean | 바꾸면 이용 시간을 넘는지. 0분(제한 없음)이면 항상 false |

- 후보: **같은 세분류**(없으면 같은 대분류) 중 코스에 없는 장소를 앞뒤 장소와 가까운 순으로 5곳 정도. 추천할 때 받아 둔 후보를 쓴다.
- `diffMinutes` 계산에 필요한 앞뒤 구간 이동시간만 새로 계산한다 (NFR-014). 대중교통 API 호출 횟수가 많으면 직선거리 추정으로 대신하고 교체 확정 때 정확히 계산하는 안도 검토한다.
- 프론트는 `exceedsAvailableTime: true` 인 후보를 **회색(선택 불가)으로 표시**해, 누르기 전에 시간 초과를 알 수 있게 한다.
- 후보가 없으면 `alternatives: []` 로 `200`.
- 오류: `404 RECOMMENDATION_NOT_FOUND`(코스 없음 또는 코스에 없는 `placeId`), `410 RECOMMENDATION_EXPIRED`.

---

## 5. 장소 교체 (REQ-029)

| 항목 | 내용 |
|---|---|
| Method · URL | `POST /api/recommendations/courses/{recommendationId}/replace` |
| 요청 DTO | `PlaceReplaceRequest` |
| 응답 | `200` · `RecommendCourseDetailResponse` (**새 `recommendationId`**) |

```json
{
  "targetPlaceId": "101",
  "newPlaceId": "201"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `targetPlaceId` | string | O | 바꿀 장소 (현재 코스에 있어야 함) |
| `newPlaceId` | string | O | 4번 API가 돌려준 후보 중 하나 |

- 바뀐 앞뒤 구간 이동시간만 다시 계산해 총 소요시간을 갱신한다.
- 교체 결과는 **새 `recommendationId`** 로 보관한다. 원래 버전도 만료 전까지 남겨 두어, 저장 API가 사용자가 실제로 본 버전을 저장할 수 있게 한다 (course-api 1.2). 만료 시각은 원래 묶음과 같다.
- 프론트는 응답의 새 ID로 상세 화면(`/results/{새 recommendationId}`)을 갱신한다.

| HTTP 상태 | 제안 오류 코드 | 발생 상황 | 화면 처리 |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | 필드 누락, `newPlaceId` 가 후보 목록에 없음 | 후보 다시 조회 |
| 404 | `RECOMMENDATION_NOT_FOUND` | 코스 없음 또는 `targetPlaceId` 가 코스에 없음 | 상세로 복귀 |
| 409 | `AVAILABLE_TIME_EXCEEDED` | 바꾸면 이용 시간을 넘음 | "선택한 시간을 넘어요" 경고 후 상세로 복귀 (코스는 그대로) |

- **시간 초과 교체는 막는다.** 서버는 교체를 반영하지 않고 `409` 를 반환하며 코스는 바뀌지 않는다. 추천 코스는 이용 시간을 넘지 않아야 하기 때문이다 (NFR-024). 프론트가 4번 응답으로 미리 회색 처리하더라도, 서버는 요청을 받을 때 다시 확인한다.
| 410 | `RECOMMENDATION_EXPIRED` | 만료 | 만료 안내 후 다시 추천 |

---

## 6. 결정 사항과 남은 항목

### 6.1 결정됨

| 항목 | 결정 | 근거 |
|---|---|---|
| 결과 0건 응답 | `200` + `courses: []`. 프론트는 안내 문구와 해결 방법(시간 늘리기·카테고리 줄이기), '조건 바꾸기' 버튼을 함께 표시 | REQ-027, course-api 2.2(빈 목록도 200)와 같은 방식 |
| 기본 체류시간 | 세분류를 확인할 수 없는 장소는 60분 | 나혜빈 결정 (2026-11-13) |
| 시간 초과 교체 | 교체를 막고 `409`. 프론트는 초과 후보를 회색으로 미리 표시 | NFR-024, 나혜빈 결정 (2026-11-13) |
| 다시 추천 제한 | 같은 사용자 기준 최근 1분에 5번까지, 넘으면 `429` | REQ-026, NFR-014, 나혜빈 결정 (2026-11-13) |

### 6.2 남은 항목

| 항목 | 확인할 내용 | 담당 |
|---|---|---|
| 공통 응답 | `ApiResponse` JSON 구조, `ErrorCode` 이름. 확정되면 이 문서의 오류 코드 이름을 맞춘다 | 정종일 |
| 장소 후보 | 장소 서비스가 넘겨줄 후보 형식(세분류 포함), 검색 반경 단계, 세분류 ↔ 카카오 분류 매핑 | 정종일·나혜빈 |
| 코스 저장 연결 | 저장 API가 `recommendationId` 로 추천 결과를 조회하는 서비스 메서드 | 나혜빈·김하윤 |
| 지도 경로 | `routeLegs` 좌표를 추천 시 함께 보관하는 범위 | 나혜빈·유지수 |
| 프론트 타입 | `features/recommend/types.ts` 필드 이름(`courseId`, `name`, `lat`/`lng`)과 이 명세 맞추기 | 나혜빈 |

추천 결과의 보관 위치와 ID 생성 방식은 API 요청·응답 형식에 영향이 없으므로 구현 단계에서 정한다. 보관은 **DB 테이블 + 만료 시각 컬럼** 방식을 우선 검토한다 (새 라이브러리 없음). DB 스키마 변경이므로 ERD 담당(김하윤)과 확인한다.

## 7. 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-11-13 | 초안 작성 |
| 2026-11-13 | 결과 0건 응답(200 + 빈 목록)·기본 체류시간 60분 확정, 보관 위치는 구현 단계로 이동 |
| 2026-11-13 | 시간 초과 교체 차단(409, 후보 회색 표시)·다시 추천 1분 5회 제한 확정, 보관은 DB 테이블 + 만료 시각 우선 검토 |
