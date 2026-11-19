# 추천 API 명세

> 담당: 나혜빈 · 작성일: 2026-11-13 · 수정일: 2026-11-18 · 추천 생성·다시 추천·코스 상세·장소 교체 후보·장소 교체 5개 API 명세입니다.
> 기준: [REQ-019 ~ 029, 050, 056](requirements.md#14-장소-및-코스-추천), 화면설계서 v2.0 RES-01 · DET-01 · DET-02 · DET-03.
> **5개 API 모두 구현되어 dev 에 반영됐습니다** (#114, #121, #125, #126, #133, #143). 이 문서는 실제 구현 기준입니다. Swagger UI(`/swagger-ui/index.html`)에서 바로 호출해 볼 수 있습니다.
> 공통 응답(`ApiResponse`)·오류 형식은 공통 코드(`global/common`, `global/exception`)를 따릅니다.

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
- 실제 응답은 공통 `ApiResponse`로 감싼다. 성공은 `{ "success": true, "data": 업무 DTO, "error": null }` 이고, **이 문서의 성공 JSON은 `data` 에 들어가는 부분 예시**다.
- 실패는 `{ "success": false, "data": null, "error": { "code", "message", "fieldErrors" } }` 이다. HTTP 상태는 오류 코드에 정해진 값으로 나간다. 외부 API 오류 원문·API 키는 응답에 넣지 않는다.
- 오류 코드는 공통 `ErrorCode` 를 구현한 enum 에서 나온다. 이 문서의 오류 코드는 아래 셋 중 하나에 속한다.

| 소속 | 위치 | 이 문서에서 쓰는 코드 |
|---|---|---|
| 공통 (`CommonErrorCode`, 정종일) | `global/exception` | `INVALID_REQUEST` 400, `INTERNAL_SERVER_ERROR` 500 |
| 추천 (`RecommendErrorCode`, 나혜빈) | `recommend/exception` | `RECOMMENDATION_NOT_FOUND` 404, `AVAILABLE_TIME_EXCEEDED` 409, `RECOMMENDATION_EXPIRED` 410, `TOO_MANY_REQUESTS` 429 |
| 장소 (`PlaceErrorCode`, 정종일) | `place/exception` | `KAKAO_API_ERROR` 502, `KAKAO_API_RATE_LIMIT` 503 |

- `TOO_MANY_REQUESTS` 는 지금은 다시 추천에서만 쓰므로 추천 쪽에 둔다. 다른 도메인에서도 필요해지면 공통으로 옮긴다.
- 장소 검색 실패는 장소 서비스가 던진 오류를 추천이 그대로 전달한다 (추천에서 따로 만들지 않음).

실패 응답 예시 (검증 실패 400):

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "입력값을 확인해 주세요.",
    "fieldErrors": [
      { "field": "availableMinutes", "message": "30 이상이어야 합니다" }
    ]
  }
}
```

실패 응답 예시 (만료 410). `fieldErrors` 는 검증 실패일 때만 들어가고, 없으면 JSON 에서 빠진다:

```json
{
  "success": false,
  "data": null,
  "error": { "code": "RECOMMENDATION_EXPIRED", "message": "추천이 만료되었습니다." }
}
```
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
- **보관 위치는 서버 메모리**다 (만료 시각과 함께 보관, DB 테이블·새 라이브러리 없음). 2시간 뒤 버리는 임시 데이터이고, 코스·장소·지도 좌표·교체 후보가 깊게 중첩되어 있어 객체 그대로 보관한다. 보관소는 인터페이스 뒤에 두어 나중에 DB·캐시로 바꿀 수 있게 한다.
- 만료된 결과는 바로 지우지 않고 일정 시간 더 남겨 두었다가 정리한다. 그동안은 `410` 으로 응답한다.
- 정리된 결과나 **서버 재시작으로 사라진 결과**는 서버가 만료 여부를 알 수 없어 `404 RECOMMENDATION_NOT_FOUND` 로 응답한다. 프론트는 `404` 도 조건 설정 화면으로 보내거나 같은 조건으로 다시 추천받게 처리한다.
- 저장한 코스(course-api)는 DB 에 따로 복사되므로 추천 결과가 사라져도 영향이 없다.

### 시간 계산 규칙

- `totalTravelMinutes = sum(places.travelMinutes) + finalTravelMinutes`
- `totalMinutes = sum(places.stayMinutes) + totalTravelMinutes` (REQ-023)
- `places[].travelMinutes` 는 **직전 지점 → 이 장소** 이동시간. 첫 장소는 출발지에서 온다.
- `finalTravelMinutes` 는 마지막 장소 → 도착지. 도착지가 없으면 `0` (돌아오는 시간은 계산하지 않음).
- 체류시간은 세분류별 예상 체류시간표(RES-01)의 **최소 ~ 최대** 범위를 쓴다: **영화·공연 150 ~ 180분, 나머지 세분류 40 ~ 90분**. 세분류를 확인할 수 없으면 **기본 40 ~ 90분**. 코스를 고를 때는 최소값으로 어림하고, 코스를 확정한 뒤 남는 시간만큼 **5분 단위**로 늘린다 (1.6 남는 시간 채우기). 응답의 `stayMinutes` 는 이렇게 정한 값이다.
- 이동시간 API가 실패해 직선거리로 계산한 구간이 하나라도 있으면 코스의 `estimated` 가 `true` 다 (NFR-025).
- **직선거리 추정 기준:** 하버사인 공식으로 직선거리(km)를 구하고 우회 보정 **1.3배**를 곱한 뒤, 평균 속도 도보 **4km/h** · 대중교통 **15km/h**(대기·환승 포함) · 차량 **20km/h**(시내 정체 감안)로 나눠 분으로 바꾼다. 소수는 **올림**한다 (이용 시간을 넘지 않게).
- **대중교통 700m 이내 구간은 도보로 계산한다.** 대중교통 API(ODsay)는 출발지와 도착지가 700m 이내면 경로를 주지 않는다. 그래서 이 구간은 도보 API(OpenRouteService)로 계산하고 `transitInfo` 에 `"도보"` 를 넣는다. 지도에는 실제 도보 경로(`ACTUAL`)로 그린다. 도보 API도 실패하면 걷는 속도(4km/h)로 직선거리 추정한다. 코스를 고를 때의 직선거리 어림값도 같은 기준(700m 이내는 걷는 속도)을 쓴다.
- 대중교통 API는 호출 사이에 최소 **300ms** 간격을 둔다. 연달아 빠르게 부르면 `429 Too Many Requests` 로 실패하기 때문이다 (#141).

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
| `availableMinutes` | integer | O | `30 ~ 360` (분). 화면 슬라이더 단계(30, 60, 120 … 360)는 프론트 규칙이고 서버는 범위만 검사한다. 0분은 추천 불가(프론트에서 추천받기 비활성), 들어오면 400 `INVALID_REQUEST` |
| `transportType` | string | O | `TRANSIT` · `WALK` · `CAR` |
| `categoryMode` | string | O | `DIRECT` · `RANDOM` |
| `categories` | string[] | X | 대분류 코드 0 ~ 3개. 중복이 오면 오류 대신 서버가 하나로 정리한다. `RANDOM` 이면 무시. `DIRECT` 인데 비어 있으면 `RANDOM` 으로 처리 |

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
  "subCategoryFilters": [
    { "category": "SPORTS", "categoryName": "스포츠활동", "subCategories": ["당구", "볼링", "클라이밍"] },
    { "category": "SHOPPING", "categoryName": "쇼핑/구경", "subCategories": ["소품샵", "팝업스토어", "시장", "백화점"] }
  ],
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
          "imageUrl": null, "openingHours": null,
          "stayMinutes": 90, "travelMinutes": 15, "transitInfo": "버스 721 · 환승 0회"
        },
        {
          "placeId": "102", "visitOrder": 2,
          "placeName": "소품샵 B", "category": "SHOPPING", "subCategory": "소품샵",
          "address": "서울 성동구 ○○로 2", "latitude": 37.5460, "longitude": 127.0420,
          "imageUrl": null, "openingHours": null,
          "stayMinutes": 40, "travelMinutes": 8, "transitInfo": "도보"
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
| `subCategoryFilters` | SubCategoryFilterResponse[] | 세분류 필터를 **대분류별로 묶은** 목록 (REQ-050). 아래 표 참고 |
| `courses` | RecommendCourseResponse[] | 추천 코스 0 ~ 3개. **정렬 없음** (결정 3) |

| `SubCategoryFilterResponse` 필드 | 타입 | 설명 |
|---|---|---|
| `category` | string | 대분류 코드 (예: `SPORTS`) |
| `categoryName` | string | 대분류 화면 이름 (예: `스포츠활동`) |
| `subCategories` | string[] | 세분류 이름 목록, 세분류 표 순서 (예: `["당구", "볼링", "클라이밍"]`) |

- `DIRECT`: 고른 대분류 순서대로, 각 대분류의 세분류 **전체**.
- `RANDOM`: 결과 코스에 **실제로 나온** 세분류만, 대분류 표 순서대로. 세분류 표에 없는 이름은 넣지 않는다.
- 코스가 0개면 `RANDOM` 은 `[]`, `DIRECT` 는 고른 대분류의 세분류 전체가 그대로 온다 (프론트는 `courses` 가 비면 필터를 숨기면 된다).
- '전체' 칩은 프론트가 붙인다. 칩을 눌렀을 때 어떤 코스를 보여줄지(걸러 보기)는 프론트가 코스 장소의 `subCategory` 로 정한다.
- 대분류별로 묶어 두어 두 가지 화면 모두 그릴 수 있다. 두 단계: `[전체][스포츠활동][쇼핑/구경]` → 스포츠활동을 누르면 `[당구][볼링][클라이밍]`. 한 줄: 묶음을 펼쳐 `[전체][당구][볼링][클라이밍][소품샵]…`

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
| `imageUrl` | string 또는 null | 썸네일. **지금은 항상 null** (사진 데이터 연결 전, P1). null이면 프론트가 카테고리 기본 이미지 |
| `openingHours` | string 또는 null | 운영시간. **지금은 항상 null** (REQ-018, P1) |
| `stayMinutes` | integer | 예상 체류시간(분). 세분류의 최소 ~ 최대 안에서 이 코스에 정한 값 |
| `travelMinutes` | integer | 직전 지점 → 이 장소 이동시간(분) |
| `transitInfo` | string 또는 null | 대중교통일 때만 값이 있다. 노선·환승 (예: `수도권 2호선 · 환승 0회`), 700m 이내 도보 구간이면 `"도보"`, 대중교통 API 실패로 추정했으면 `null`. 도보·차량은 항상 `null` |

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

- 서버는 반경을 **1km → 2km → 3km → 5km** 로 넓혀 다시 찾은 뒤에도 없을 때 빈 목록을 반환한다 (REQ-015).
- 프론트는 `courses` 가 비면 "조건에 맞는 코스가 없어요. 시간을 늘리거나 카테고리를 줄여보세요" 안내와 '조건 바꾸기' 버튼을 보여준다. 문구는 화면 담당이 정한다.
- 코스가 1 ~ 2개뿐이면 있는 만큼만 반환한다.

### 1.5 실패 응답

| HTTP 상태 | 오류 코드 (소속) | 발생 상황 | 화면 처리 |
|---|---|---|---|
| 400 | `INVALID_REQUEST` (공통) | 필수값 누락, 좌표 범위·시각 형식·이용 시간 범위(30 ~ 360)·대분류 개수(4개 이상)·잘못된 enum 값 | 조건 확인 안내 |
| 502 | `KAKAO_API_ERROR` (장소) | 장소 검색 외부 API(카카오 로컬)가 실패해 후보를 하나도 얻지 못함 | "잠시 후 다시 시도해 주세요" |
| 503 | `KAKAO_API_RATE_LIMIT` (장소) | 장소 검색 외부 API 호출 한도 초과 | "잠시 후 다시 시도해 주세요" |
| 500 | `INTERNAL_SERVER_ERROR` (공통) | 서버 처리 실패 | 오류 안내, 재시도 가능 |

- 장소 검색 오류는 장소 서비스가 던진 오류를 추천이 그대로 전달한다. 단, 좁은 반경에서 이미 코스를 찾은 뒤 반경을 넓히다가 실패하면 오류 대신 **찾은 코스만으로 `200`** 응답한다 (1.4 "있는 만큼만").
- 이동시간 API 실패는 오류로 응답하지 않는다. 직선거리로 계산하고 `estimated: true` 로 표시한다 (REQ-022).

### 1.6 서버 처리 순서

1. 요청 검증. `DIRECT` + 빈 `categories` 는 `RANDOM` 으로 바꾼다.
2. 이용 시간으로 장소 수 상한을 정한다: 60분 이하 → 1곳, 180분 이하 → 2곳, 300분 이하 → 3곳, 그 이상 → 4곳 (REQ-019).
3. 장소 도메인 서비스(`NearbyPlaceService`)로 반경 1km 안의 후보 장소를 받아 합친다 (아래 검색 지점). 일부 지점만 검색에 실패하면 나머지 지점 후보로 계속하고, 모든 지점이 실패했을 때만 장소 오류를 전달한다.
4. 상한 안에서 장소를 고르고 방문 순서를 정해 코스를 만든다 (아래 코스 구성 규칙). 고르는 동안은 최소 체류시간과 직선거리로 어림하고, **최종 코스만** 고른 이동수단 API 하나로 계산한다 (NFR-014).
5. 실제 이동시간으로 이용 시간을 넘으면 마지막 장소를 빼고 다시 확인하고, 한 곳만 남겨도 넘으면 버린다 (REQ-024). 들어오면 남는 시간을 체류시간으로 채운다 (아래 남는 시간 채우기, API 호출 없음).
6. 서로 다른 코스 최대 3개를 골라 2시간 보관하고 응답한다. 3개가 안 되면 반경을 넓혀 **모자란 만큼만** 3 ~ 5를 반복한다. 넓히는 단계는 도보 1km → 2km, 대중교통·차량 1km → 2km → 3km → 5km.

**검색 지점** (#204)

| 조건 | 지점 | 이유 |
|---|---|---|
| 도착지 없음 · 도착지가 1km 안 | 출발지 + **먼 지점** (출발지에서 도보 1.4km · 대중교통·차량 3.5km, 방향은 매번 무작위) | 카카오 검색은 가까운 순으로 정해진 개수만 주므로, 가게가 많은 동네는 출발지 주변만으로 다 차서 먼 장소가 후보에 안 들어온다. "조금 먼 코스" 재료를 따로 찾는다 |
| 경로 모드 (도착지 1km 이상) | 출발지 · 1/3 지점 · 2/3 지점 · 도착지 | 가는 길 전체에서 고르게 찾는다 |

지점 수만큼 장소 검색 호출이 늘어난다 (보통 2배, 경로 모드 4배, NFR-014). 반경을 넓힐 때도 모든 지점에서 넓힌다.

**남는 시간 채우기** (#204)

| 규칙 | 내용 |
|---|---|
| 목표 시간 | 코스마다 이용 시간의 **80% / 90% / 100%** 까지 채운다 (예: 2시간 → 96 / 108 / 120분). 세 코스의 총 시간이 서로 다르게 나온다. 소수점은 버린다 |
| 채우는 방법 | 장소를 돌아가며 체류시간을 **5분씩** 늘린다. 장소마다 최대 체류시간까지만 늘리고, 모두 최대면 남은 시간은 그대로 둔다 |
| 이미 목표보다 길 때 | 늘리지 않는다 (예: 2시간, 2곳 최소 80분 + 이동 20분 = 100분 > 목표 96분 → 그대로 100분) |

**코스 구성 규칙**

| 규칙 | 내용 |
|---|---|
| 대분류 (직접 선택) | 고른 대분류를 번갈아 넣는다. 코스마다 시작 대분류를 바꿔 다양하게 만든다. 시간이 부족하면 일부 대분류만 들어갈 수 있다 (REQ-020) |
| 세분류 | 한 코스 안에 같은 세분류는 두 번 넣지 않는다 (예: 카페 → 카페 없음). 랜덤도 같다 |
| 음식·카페 1곳 | 음식/맛집, 카페/디저트는 한 코스에 각각 1곳만 넣는다 (한식 → 일식, 카페 → 디저트 없음 / 한식 → 카페 는 가능). 음식/맛집만 골랐으면 1곳짜리 코스가 된다 |
| 같은 브랜드 | 같은 브랜드(체인)는 두 번 넣지 않는다. 장소 이름의 마지막 단어가 '점' 으로 끝나면 지점명으로 보고 떼어 비교한다 (예: "메가MGC커피 성수역점" → "메가MGC커피") |
| 장소 수 | 상한 안에서 한 곳씩 넣으며, 어림한 총 소요시간(최소 체류시간 기준)이 이용 시간 안인 장소만 넣는다. 넣을 장소가 없으면 멈춘다 (예: 3시간 + 영화 150분 → 1곳, REQ-019) |
| 목표에 맞는 후보 먼저 (거리 비율) | 장소를 넣을 자리마다 후보를 "목표와 어긋난 거리 + 무작위 거리(도보 0 ~ 0.5km, 대중교통·차량 0 ~ 1.25km)" 순으로 줄 세워 앞에서부터 고른다. 목표에 맞는 곳이 잘 뽑히되, 비슷한 곳끼리는 순서가 섞여 코스가 다양해진다. 목표는 "먼저 볼 순서" 일 뿐 걸러 내는 조건이 아니다 (목표 근처에 맞는 장소가 없으면 다른 장소를 고른다) |
| 목표 (보통) | 코스마다 출발지에서 목표 거리 = 최대 반경 × **15% / 40% / 70%** (도보 0.3 / 0.8 / 1.4km, 대중교통·차량 0.75 / 2 / 3.5km). 어긋난 거리 = \|출발지 → 장소 − 목표 거리\| |
| 목표 (경로 모드) | 장소의 경로 위 위치 = 출발→장소 ÷ (출발→장소 + 장소→도착) (0 = 출발지, 1 = 도착지). 자리마다 목표 위치를 경로를 (장소 수 + 1) 칸으로 나눈 경계에 둔다 (2곳: 0.33 · 0.67, 3곳: 0.25 · 0.5 · 0.75). 코스마다 **−0.1 / 0 / +0.1** 을 더한다. 어긋난 거리 = \|위치 − 목표 위치\| × 출발지 → 도착지 거리 + **우회 거리**(출발→장소 + 장소→도착 − 출발→도착) |
| 한 구간 최대 이동시간 | 출발지 → 장소, 장소 → 장소 구간의 어림 이동시간이 도보 25분 · 대중교통 40분 · 차량 30분을 넘는 장소는 넣지 않는다. 마지막 장소 → 도착지 구간은 보지 않는다 |
| 경로 모드 우회 제한 | 우회 거리가 도보 1km · 대중교통·차량 2.5km를 넘는 장소(가는 길을 많이 벗어난 곳)는 넣지 않는다 |
| 방문 순서 | 보통: 출발지에서 가장 가까운 곳 → 그다음 가까운 곳 (직선거리). 경로 모드: 도착지 쪽으로 점점 가까워지는 순 (출발지→장소 − 장소→도착지 가 작은 곳부터) |
| 같은 코스 | 장소 묶음이 같으면 같은 코스로 보고 하나만 남긴다 |
| 대중교통 경로 | ODsay 경로 후보 중 첫 번째(ODsay 추천 순서)를 쓰고, 한 구간에 노선이 여러 개면 첫 노선을 표시한다. 700m 이내 구간은 도보로 계산한다 (시간 계산 규칙) |

---

## 2. 다시 추천 (REQ-026)

| 항목 | 내용 |
|---|---|
| Method · URL | `POST /api/recommendations/{recommendationSetId}/regenerate` |
| 요청 본문 | 없음 (서버에 보관한 조건을 그대로 사용) |
| 응답 | `200` · `RecommendResponse` (새 `recommendationSetId`) |

- 같은 조건으로 **다른 장소·조합**을 추천한다. 직전 묶음에 나온 장소는 가능한 한 제외한다. 랜덤 추천에도 같게 적용한다.
- 후보가 모자라면 겹치는 장소가 나올 수 있고, 그래도 없으면 1.4와 같은 빈 결과를 반환한다.

| HTTP 상태 | 오류 코드 (소속) | 발생 상황 | 화면 처리 |
|---|---|---|---|
| 404 | `RECOMMENDATION_NOT_FOUND` (추천) | 없는 묶음 ID | 조건 설정으로 이동 |
| 410 | `RECOMMENDATION_EXPIRED` (추천) | 2시간이 지나 조건이 사라짐 | 프론트가 가진 조건으로 추천 생성 API 호출 |
| 429 | `TOO_MANY_REQUESTS` (추천) | 같은 사용자가 최근 1분 안에 다시 추천을 5번 넘게 요청 | "잠시 후 다시 시도해 주세요" |

- **다시 추천 제한:** 같은 사용자 기준 **최근 1분 동안 5번까지** 허용하고 6번째부터 `429` 를 반환한다. 다시 추천은 호출할 때마다 새 `recommendationSetId` 가 생기므로 묶음 ID가 아니라 사용자 기준으로 센다.
- **사용자 구분은 요청 IP** 로 한다. 비회원도 이용하므로 로그인 정보로는 구분할 수 없다. `429` 로 막힌 요청은 횟수에 세지 않는다 (1분이 지나면 다시 가능).
- 배포 때 Nginx 같은 프록시 뒤에 두면 모든 요청 IP가 프록시 IP 하나로 보인다. 이때는 `server.forward-headers-strategy: framework` 설정이 필요하다 (배포 담당과 확인).
- 제한 확인은 외부 API를 부르기 전에 맨 먼저 한다. 외부 API 무료 한도를 지키기 위한 제한이다 (NFR-014).
- 첫 추천 생성(`POST /api/recommendations`)은 제한하지 않는다.

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
| `places[].description` | string 또는 null | 소개 (펼침 영역). **지금은 항상 null** (소개 데이터 연결 전, P1) |
| `places[].kakaoMapUrl` | string 또는 null | 카카오 장소 페이지 링크 ('카카오맵에서 보기'). 카카오 로컬 응답의 `place_url` |
| `routeLegs` | RouteLegResponse[] | 지도 경로. 형식은 [course-api.md 3.2](course-api.md) `CourseRouteLegResponse` 와 같다. 아래 표 참고 |

`RouteLegResponse`: `fromVisitOrder`, `toVisitOrder`(출발지 0, 장소 1 ~ N, 도착지가 있으면 N + 1), `pathType`, `coordinates`(진행 방향 순서, 2개 이상).

| `pathType` | 언제 | `coordinates` |
|---|---|---|
| `ACTUAL` | 차량·도보 API 성공, 대중교통의 700m 이내 도보 구간 | API가 준 실제 경로 좌표 |
| `STRAIGHT` | 대중교통 구간(1차 버전), API 실패로 직선거리 추정한 구간 | 앞 지점과 뒤 지점 2개 |

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
      "travelMinutes": 12, "transitInfo": null,
      "diffMinutes": -3, "exceedsAvailableTime": false, "estimated": true
    }
  ]
}
```

| `AlternativePlaceResponse` 필드 | 타입 | 설명 |
|---|---|---|
| `placeId` ~ `stayMinutes` | | `RecommendPlaceResponse` 와 같은 의미 |
| `travelMinutes` | integer | 직전 지점 → 후보 장소 예상 이동시간 (직선거리 추정) |
| `transitInfo` | null | **항상 null.** 후보 목록에서는 이동시간 API를 부르지 않아 노선 정보가 없다 |
| `diffMinutes` | integer | 이 후보로 바꿨을 때 총 소요시간 변화(예상). 음수면 줄어듦 |
| `exceedsAvailableTime` | boolean | 바꾸면 이용 시간을 넘는지 (예상 시간 기준) |
| `estimated` | boolean | 이동시간이 직선거리 추정이면 true. **지금은 항상 true**. 프론트는 "약 12분" 처럼 예상 시간으로 표시한다 (NFR-025) |

- 후보: 바꿀 장소와 **같은 대분류** 중 코스에 없는 장소, 최대 **5곳**. **같은 세분류를 먼저**, 그다음 같은 대분류의 다른 세분류 순서이고, 각각 앞뒤 장소를 거쳐 가는 거리가 짧은 순이다. 추천할 때 받아 둔 후보를 쓴다 (장소 API를 다시 부르지 않음).
- 후보 목록에서는 **이동시간 API를 부르지 않고 직선거리로 추정**한다. 후보 5곳마다 앞뒤 구간을 부르면 외부 API 호출이 너무 많아지기 때문이다 (NFR-014). 정확한 시간은 5번 교체 확정 때 실제 API로 다시 계산한다.
- 그래서 확정 후 실제 총 소요시간은 `diffMinutes` 로 예상한 값과 조금 다를 수 있다.
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

- 바뀐 앞뒤 구간 이동시간만 고른 이동수단 API로 다시 계산해 총 소요시간을 갱신한다. 마지막 장소를 바꾸면 도착지까지 구간도 다시 계산한다.
- 새 장소의 체류시간은 **원래 장소의 체류시간을 새 장소의 최소 ~ 최대 안으로 맞춘 값**이다 (예: 카페 60분 → 베이커리 60분, 카페 60분 → 영화 150분). 교체해도 총 시간이 크게 흔들리지 않게 한다. 후보 조회(4장)의 `stayMinutes`·`diffMinutes` 도 같은 규칙으로 계산한다 (#204).
- 교체 결과는 **새 `recommendationId`** 로 보관한다. 원래 코스는 바뀌지 않고 만료 전까지 그대로 남는다. 저장 API가 사용자가 실제로 본 버전을 저장할 수 있게 하기 위해서다 (course-api 1.2). 새 코스의 `recommendationSetId` 와 만료 시각은 원래 묶음과 같다.
- **프론트는 응답의 새 ID를 써야 한다.** 상세 화면을 `/results/{새 recommendationId}` 로 바꾸고, 이후 저장·지도·다시 교체도 새 ID로 요청한다. 옛 ID로 저장하면 교체 전 코스가 저장된다.

| HTTP 상태 | 오류 코드 (소속) | 발생 상황 | 화면 처리 |
|---|---|---|---|
| 400 | `INVALID_REQUEST` (공통) | 필드 누락, `newPlaceId` 가 후보 목록에 없음 | 후보 다시 조회 |
| 404 | `RECOMMENDATION_NOT_FOUND` (추천) | 코스 없음 또는 `targetPlaceId` 가 코스에 없음 | 상세로 복귀 |
| 409 | `AVAILABLE_TIME_EXCEEDED` (추천) | 바꾸면 이용 시간을 넘음 | "선택한 시간을 넘어요" 경고 후 상세로 복귀 (코스는 그대로) |
| 410 | `RECOMMENDATION_EXPIRED` (추천) | 만료 | 만료 안내 후 다시 추천 |

- **시간 초과 교체는 막는다.** 서버는 교체를 반영하지 않고 `409` 를 반환하며 코스는 바뀌지 않는다. 추천 코스는 이용 시간을 넘지 않아야 하기 때문이다 (NFR-024). 프론트가 4번 응답으로 미리 회색 처리하더라도, 서버는 요청을 받을 때 다시 확인한다.

---

## 6. 결정 사항과 남은 항목

### 6.1 결정됨

| 항목 | 결정 | 근거 |
|---|---|---|
| 결과 0건 응답 | `200` + `courses: []`. 프론트는 안내 문구와 해결 방법(시간 늘리기·카테고리 줄이기), '조건 바꾸기' 버튼을 함께 표시 | REQ-027, course-api 2.2(빈 목록도 200)와 같은 방식 |
| 체류시간 | 최소 ~ 최대: 영화·공연 150 ~ 180분, 나머지 세분류 40 ~ 90분. 세분류를 확인할 수 없는 장소도 40 ~ 90분. 고를 때는 최소, 확정 뒤 남는 시간만큼 5분 단위로 늘린다 | 팀 회의 결정 (2026-11-18 최소값, 2026-11-19 최대값 추가 #204) |
| 코스 다양화 | 시간 비율(이용 시간 80 / 90 / 100% 까지 채우기), 거리 비율(보통: 최대 반경 15 / 40 / 70%, 경로 모드: 자리마다 목표 위치 + 코스마다 −0.1 / 0 / +0.1), 검색 지점 추가(먼 지점, 경로 1/3·2/3 지점), 교체 시 체류시간 이어받기 | 팀 회의 결정, 나혜빈 (#204, 2026-11-19) |
| 직선거리 추정 기준 | 우회 1.3배, 도보 4 · 대중교통 15 · 차량 20 km/h, 올림 | REQ-022, NFR-025, 나혜빈 결정 (2026-11-16) |
| 시간 초과 교체 | 교체를 막고 `409`. 프론트는 초과 후보를 회색으로 미리 표시 | NFR-024, 나혜빈 결정 (2026-11-13) |
| 다시 추천 제한 | 같은 사용자 기준 최근 1분에 5번까지, 넘으면 `429` | REQ-026, NFR-014, 나혜빈 결정 (2026-11-13) |
| 공통 응답·오류 형식 | dev 에 반영된 `ApiResponse`·`ErrorCode` 를 따른다. 오류 코드 소속은 공통 규칙 표 참고 | 정종일 공통 코드 반영 (2026-11-16) |
| 오류 코드 소속 | `TOO_MANY_REQUESTS` 는 추천 쪽(필요하면 나중에 공통으로 이동), 장소 검색 실패는 장소 쪽에서 만들고 추천은 그대로 전달 | 나혜빈·정종일 합의 (2026-11-16) |
| 장소 검색 실패 오류 코드 | `PlaceErrorCode` 의 `KAKAO_API_ERROR` 502, `KAKAO_API_RATE_LIMIT` 503 | 정종일 반영 (#81) |
| 이용 시간 검증 | 서버는 `30 ~ 360` 범위만 검사. 슬라이더 단계는 프론트 규칙 | 나혜빈 결정 (2026-11-16) |
| 대분류 중복 | 400 대신 서버에서 중복을 하나로 정리 | 나혜빈 결정 (2026-11-16) |
| 검색 반경 단계 | 1km → 2km → 3km → 5km. 코스가 3개가 안 되면 넓혀서 모자란 만큼만 다시 찾는다 | REQ-015, 정종일 제안·나혜빈 확정 (2026-11-17) |
| 이동수단별 제한·경로 모드 | 최대 반경 도보 2km·나머지 5km, 한 구간 최대 도보 25분·대중교통 40분·차량 30분, 가까운 후보 먼저(#204 에서 "목표에 맞는 후보 먼저" 로 변경), 도착지 1km 이상이면 경로 모드(여러 지점 검색·우회 제한·도착지 방향 순서), 음식·카페는 한 코스에 1곳, 같은 브랜드 두 번 금지. 숫자는 실제 결과를 보며 조정 | 팀 회의 피드백, 나혜빈 (#170, 2026-11-19) |
| 장소 후보 | 장소 서비스 `NearbyPlaceService.findCandidates` 결과(세분류 이름 포함)를 추천 쪽 후보 모양으로 바꿔 쓴다. 세분류를 정할 수 없는 장소는 장소 쪽에서 제외 | 정종일 (#81), 나혜빈 (#83) |
| 코스 구성 규칙 | 1.6 코스 구성 규칙 표 | REQ-019·020, NFR-014, 나혜빈 결정 (2026-11-17) |
| 보관 위치 | 서버 메모리 + 만료 시각. 정리 이후·서버 재시작 후의 ID 는 `404` | REQ-056, 나혜빈 결정 (2026-11-17) |
| 세분류 필터 형식 | 대분류별로 묶은 `SubCategoryFilterResponse[]`. 화면은 두 단계(대분류 → 세분류) 필터 | REQ-050, 나혜빈 (#121) |
| 다시 추천 사용자 구분 | 요청 IP. 막힌 요청은 횟수에 세지 않음 | 나혜빈 (#125) |
| 코스 저장 연결 | 저장(코스 도메인)은 `RecommendService.getCourseDetail(recommendationId)` 로 상세 응답을 받아 쓴다. 다른 도메인이 추천 보관소를 직접 보지 않는다 | 나혜빈·김하윤 (#126) |
| 지도 경로 | 추천할 때 받은 경로 좌표를 코스와 함께 보관하고, 상세 조회에서 `routeLegs` 로 돌려준다 (API 다시 호출 안 함) | 나혜빈 (#126) |
| 교체 후보 이동시간 | 직선거리 추정(`estimated: true`, `transitInfo: null`). 교체 확정 때만 실제 API | NFR-014, 나혜빈 (#133) |
| 교체 후 ID | 교체하면 새 `recommendationId`, 원래 코스는 그대로 | course-api 1.2, 나혜빈 (#133) |
| 대중교통 700m 이내 | 도보 API로 계산, `transitInfo: "도보"`. ODsay 호출 간격 300ms | 나혜빈 (#141) |
| 대분류·세분류 enum 통일 | 공통 enum `global/common/category` 의 `Category`·`SubCategory` 를 추천·장소·코스가 함께 쓴다. 장소의 카카오 검색 규칙은 `place/domain/SubCategorySearchRule`. enum 사이 변환 코드 삭제 | 팀 회의 결정, 나혜빈·정종일 (#174, 2026-11-19) |

### 6.2 남은 항목

| 항목 | 확인할 내용 | 담당 |
|---|---|---|
| 세분류 ↔ 카카오 분류 | 장소 쪽 분류 규칙(토큰)이 실제 카카오 응답에서 잘 맞는지 확인·보정 | 정종일·나혜빈 |
| 사진·운영시간·소개 | `imageUrl`·`openingHours`·`description` 이 지금은 null. 데이터 출처(TourAPI 등) 연결 (P1) | 나혜빈·정종일 |
| 대중교통 지도 경로 | 대중교통 구간은 지금 직선(`STRAIGHT`). 실제 경로는 2차 버전 | 나혜빈·유지수 |
| 프론트 타입 | `features/recommend/types.ts` 필드 이름(`courseId`, `name`, `lat`/`lng`)과 이 명세 맞추기 (RES-01 연결 때) | 나혜빈 |
| 배포 시 IP | 프록시 뒤 배포면 `server.forward-headers-strategy: framework` 필요 | 김용현 |

추천 결과는 서버 메모리에 보관하므로 ERD 의 `recommendation_history` 테이블은 2시간 보관에 쓰지 않는다. 이 테이블은 관리자 추천 이력 조회(REQ-047, P2)용 기록으로 남겨 두고, 그 기능을 만들 때 추천 시 기록을 남기는 기능을 추가한다. 만료 후 정리까지 남겨 두는 시간과 ID 생성 방식은 구현 단계에서 정한다.

## 7. 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-11-13 | 초안 작성 |
| 2026-11-13 | 결과 0건 응답(200 + 빈 목록)·기본 체류시간 60분 확정, 보관 위치는 구현 단계로 이동 |
| 2026-11-13 | 시간 초과 교체 차단(409, 후보 회색 표시)·다시 추천 1분 5회 제한 확정, 보관은 DB 테이블 + 만료 시각 우선 검토 |
| 2026-11-14 | 이용 시간 0분 = 추천 불가로 수정 (시간 제한 없음 아님). 0분 요청은 400 |
| 2026-11-16 | 공통 `ApiResponse`·`ErrorCode` 반영: 실패 응답 예시 추가, 오류 코드 소속(공통·추천·장소) 표시, 이용 시간 검증을 `30 ~ 360` 으로, 대분류 중복은 서버에서 정리 |
| 2026-11-16 | 직선거리 추정 기준 확정 (우회 1.3배, 도보 4 · 대중교통 15 · 차량 20 km/h, 올림) |
| 2026-11-17 | 장소 오류 코드 확정(`KAKAO_API_ERROR` 502·`KAKAO_API_RATE_LIMIT` 503), 검색 반경 1·2·3·5km, 코스 구성 규칙, 보관 위치 서버 메모리(정리 후·재시작 후 404) 반영 |
| 2026-11-18 | 구현 완료 반영: 세분류 필터를 대분류별 묶음으로(1.3), 다시 추천 사용자 구분 = IP(2장), `routeLegs` 의 `pathType` 규칙(3장), 교체 후보 `estimated` 추가·`transitInfo` null·후보 고르는 순서(4장), 교체 후 새 ID 사용 안내(5장), 대중교통 700m 이내 도보 계산·ODsay 호출 간격(시간 계산 규칙), 사진·운영시간·소개는 지금 null, 6장 정리 |
| 2026-11-18 | 체류시간 변경: 영화·공연 150분 유지, 나머지 세분류와 기본값 40분 (팀 회의) |
| 2026-11-19 | 코스 구성 개선(#170): 이동수단별 최대 반경·한 구간 최대 이동시간, 가까운 후보 먼저, 도착지가 멀면 경로 모드(출발·중간·도착 검색, 우회 제한, 도착지 방향 순서), 음식·카페 한 코스 1곳, 같은 브랜드 두 번 금지 |
| 2026-11-19 | 대분류·세분류 enum 을 공통(`global/common/category`)으로 통일 (#174). 6.2 "enum 중복" 항목을 6.1 결정됨으로 이동 |
| 2026-11-19 | 코스 다양화(#204): 체류시간 최소 ~ 최대(40 ~ 90분, 영화·공연 150 ~ 180분)와 남는 시간 채우기(80 / 90 / 100%), 거리 비율(목표에 맞는 후보 먼저), 검색 지점 추가(먼 지점, 경로 1/3·2/3), 일부 지점 검색 실패 시 계속, 교체 시 체류시간 이어받기 |
