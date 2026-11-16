# 저장 코스 스냅샷 DB 설계 검토안

> 담당: 김하윤 · 작성일: 2026-11-16 · 이슈: [#31](https://github.com/teumteum-sesac/teumteum/issues/31)
> 기존 [저장 코스 API](course-api.md), [ERD](erd.md), [추천 API](recommend-api.md)를 구체화했다. 사용자 요청에 따라 ERD의 신규 생성용 DDL에 반영했으며 PR에서 팀 리뷰한다. 실제 DB에는 적용하지 않았다.

## 1. 저장 구조

- `course`: 코스 전체의 저장 당시 정보와 원본 추천 버전 ID.
- `course_place`: 장소 참조와 함께 저장 당시 장소 정보·일정을 복사한다.
- `course_route_leg` 신규: 지도 구간별 경로를 보관한다. 한 구간의 좌표 배열은 JSON으로 저장한다.
- 최신 장소 조회 결과는 저장 코스 테이블에 기록하지 않는다. 요청 응답으로만 반환한다.

저장은 서버가 보관한 추천 버전의 데이터를 복사한다. 저장 직전 외부 API나 갱신된 공용 `place` 값으로 다시 구성하지 않는다. 필수 데이터가 없으면 불완전한 저장본을 만들지 않는다.

## 2. `course` 필드 대응표

| API 필드 | DB 컬럼 | 자료형·NULL | 변경·의미 |
|---|---|---|---|
| `courseId` | `course_id` | BIGINT NOT NULL, PK | 기존, API에서는 십진 문자열 |
| 인증 회원 ID | `member_id` | BIGINT NOT NULL, FK | 기존, 요청 본문으로 받지 않음 |
| `sourceRecommendationId` | `source_recommendation_id` | VARCHAR(128) NOT NULL | 추가, 원본 추천 코스 1건의 불변 버전 ID |
| `courseName` | `course_name` | VARCHAR(810) NOT NULL | 기존 200에서 확대 제안, 장소명 최대 200자 × 4 + 구분자 여유 |
| `origin.name` | `origin_name` | VARCHAR(200) NULL | 기존 |
| `origin.address` | `origin_address` | VARCHAR(500) NULL | 추가 |
| `origin.latitude` | `origin_latitude` | DECIMAL(10,7) NOT NULL | 기존 |
| `origin.longitude` | `origin_longitude` | DECIMAL(10,7) NOT NULL | 기존 |
| `destination.name` | `destination_name` | VARCHAR(200) NULL | 기존 |
| `destination.address` | `destination_address` | VARCHAR(500) NULL | 추가 |
| `destination.latitude` | `destination_latitude` | DECIMAL(10,7) NULL | 기존 |
| `destination.longitude` | `destination_longitude` | DECIMAL(10,7) NULL | 기존 |
| `transportType` | `transport_type` | VARCHAR(20) NOT NULL | 기존, TRANSIT / WALK / CAR |
| `startTime` | `start_time` | TIME NOT NULL | 기존, 00:00~23:30, 30분 단위 |
| `totalMinutes` | `total_minutes` | SMALLINT NOT NULL | 기존 |
| `totalTravelMinutes` | `total_travel_minutes` | SMALLINT NOT NULL | 기존 |
| `finalTravelMinutes` | `final_travel_minutes` | SMALLINT NOT NULL | 기존, 도착지 없으면 0 |
| `estimated` | `estimated` | BOOLEAN NOT NULL | 추가, 추천 결과 값을 그대로 복사, 누락 시 false로 추측하지 않음 |
| `createdAt` | `created_at` | DATETIME NOT NULL | 기존, 서버 저장 시각 |

시각 저장·변환은 팀 공통 시간대 설정을 따른다. DATETIME 자체에는 오프셋이 없으므로 API 직렬화 시 명시적으로 오프셋을 붙인다. `start_time`은 날짜 없는 현지 출발 시각이다.

### 중복 저장·조회

- UNIQUE `(member_id, source_recommendation_id)`로 같은 회원의 같은 추천 버전은 한 건만 보관한다.
- 추천 ID 비교는 대소문자를 구분하는 `utf8mb4_bin` 컬럼 collation을 제안한다. 기본 `utf8mb4_unicode_ci`를 그대로 쓰면 다른 ID를 동일하게 취급할 수 있다.
- 원본 추천 ID에는 추천 결과 테이블 FK를 걸지 않는다. 추천 결과가 2시간 후 제거돼도 저장본은 남아야 한다.
- 중복 요청이면 추천 만료 확인보다 기존 저장본 조회를 먼저 한다. 기존 ID·저장 시각을 유지하고 200을 반환한다.
- 동시 요청은 UNIQUE 제약으로 최종 방어한다. 중복 키로 저장이 취소된 트랜잭션을 종료한 뒤 새 트랜잭션으로 기존 저장본을 조회한다. 다른 DB 오류를 중복 저장으로 처리하지 않는다.
- 목록 인덱스는 기존 `(member_id, created_at)`을 `(member_id, created_at, course_id)`로 교체 제안한다. `created_at DESC, course_id DESC` 정렬에 맞춘다.

`placeCount`는 저장 장소 수로 조회한다. `availableMinutes`·카테고리 선택 조건은 현재 저장 상세 응답에 없으므로 이번 변경에는 추가하지 않는다.

## 3. `course_place` 필드 대응표

이 테이블의 장소 정보 컬럼은 모두 스냅샷이다. 별도 `snapshot_` 접두사 없이 테이블의 의미로 구분한다.

| API 필드 | DB 컬럼 | 자료형·NULL | 변경 |
|---|---|---|---|
| `coursePlaceId` | `course_place_id` | BIGINT NOT NULL, PK | 기존 |
| 부모 코스 | `course_id` | BIGINT NOT NULL, FK | 기존 |
| `placeId` | `place_id` | BIGINT NOT NULL, FK | 기존, 최신 정보 조회 연결용 |
| `visitOrder` | `visit_order` | SMALLINT NOT NULL | 기존 |
| `placeName` | `place_name` | VARCHAR(200) NOT NULL | 추가 |
| `category` | `category_code` | VARCHAR(30) NOT NULL | 추가, 저장 당시 대분류 코드 |
| `subCategory` | `sub_category_name` | VARCHAR(80) NOT NULL | 추가, 저장 당시 세분류 이름 |
| `address` | `address` | VARCHAR(500) NULL | 추가 |
| `latitude` | `latitude` | DECIMAL(10,7) NOT NULL | 추가 |
| `longitude` | `longitude` | DECIMAL(10,7) NOT NULL | 추가 |
| `imageUrl` | `image_url` | VARCHAR(2048) NULL | 추가, URL 문자열만 보관 |
| `description` | `description` | TEXT NULL | 추가 |
| `openingHours` | `opening_hours` | TEXT NULL | 추가, 제공자별 형식 차이를 허용 |
| `stayMinutes` | `stay_minutes` | SMALLINT NOT NULL | 기존 |
| `travelMinutes` | `travel_minutes` | SMALLINT NOT NULL | 기존 |
| `transitInfo` | `transit_info` | VARCHAR(200) NULL | 기존 |

- 기존 UNIQUE `(course_id, visit_order)`와 부모 삭제 시 CASCADE를 유지한다.
- 대분류·세분류는 공용 카테고리 테이블 FK 대신 값 자체를 보관한다. 분류 이름이 바뀌어도 저장본을 유지한다.
- `place_id` FK는 기존 삭제 제한을 유지한다. 참조 중인 공용 장소를 물리 삭제하지 않고, 외부 정보 없음은 최신 조회의 NOT_FOUND로 표현한다. 장소 캐시 정리 정책과 이 방식을 확인해야 한다.
- API 명세상 `subCategory`는 필수다. 추천에서 세분류 미확인 후보도 허용하므로 저장 전에 어떤 문자열을 제공할지 추천 담당과 확인한다. 임의 분류를 만들어 저장하지 않는다. NULL 허용으로 바꾸려면 API도 함께 수정해야 한다.
- 빈 선택 정보는 NULL로 저장한다. 필수 필드 누락·길이 초과는 검증 실패로 처리하고 문자열을 조용히 잘라 저장하지 않는다.

## 4. 신규 `course_route_leg`

| API 필드 | DB 컬럼 | 자료형·NULL | 의미 |
|---|---|---|---|
| 내부 PK | `course_route_leg_id` | BIGINT NOT NULL, AUTO_INCREMENT | 구간 식별자 |
| 부모 코스 | `course_id` | BIGINT NOT NULL, FK | `course.course_id`, ON DELETE CASCADE |
| `fromVisitOrder` | `from_visit_order` | SMALLINT NOT NULL | 출발지 0, 장소 1~N |
| `toVisitOrder` | `to_visit_order` | SMALLINT NOT NULL | 다음 장소, 도착지는 N+1 |
| `pathType` | `path_type` | VARCHAR(10) NOT NULL | ACTUAL / STRAIGHT |
| `coordinates` | `coordinates` | JSON NOT NULL | 진행 방향 순서의 좌표 객체 배열 |

좌표 예시:

```json
[
  {"latitude": 37.544, "longitude": 127.055},
  {"latitude": 37.5475, "longitude": 127.061}
]
```

- UNIQUE `(course_id, from_visit_order)`로 구간 중복을 막고 `from_visit_order` 순서로 반환한다.
- 구간은 `to_visit_order = from_visit_order + 1`. 장소 N개이면 도착지 없을 때 N개, 있을 때 N+1개다.
- 좌표는 2개 이상이며 구간의 출발·도착 저장 좌표와 연결돼야 한다. 비교 허용 오차는 추천·지도 담당과 확인한다.
- 좌표 배열은 지도에 한 번에 전달하고 내부 좌표를 DB 검색하지 않으므로 별도 좌표 행보다 JSON을 제안한다. JSON 매핑은 기존 JPA 환경에서 구현하며 새 의존성을 전제로 하지 않는다.
- 좌표 개수·구간별 데이터 크기 상한은 추천·지도 담당과 정한다. 저장 단계에서 임의로 단순화하거나 잘라 원래 경로를 바꾸지 않는다.
- 지도 직선 표시와 이동시간 추정은 다르다. 대중교통이 STRAIGHT여도 실제 시간 API가 성공했다면 `course.estimated = false`일 수 있다.

## 5. 무결성 검증

| DB 제약으로 방어할 항목 | 서비스에서 함께 검증할 항목 |
|---|---|
| 회원·부모 코스·공용 장소 FK, PK·UNIQUE | 인증 회원 소유권·추천 접근 권한·만료 |
| 좌표 범위 CHECK: 위도 -90~90, 경도 -180~180 | JSON 내부 좌표의 범위·객체 구조·개수·크기 |
| `visit_order` BETWEEN 1 AND 4 | 장소 1~4개, 방문 순서가 1부터 연속됨 |
| 시간 값 0 이상, 총 시간 360 이하 CHECK | 총 시간 = 체류 합 + 이동 합 + 마지막 이동 |
| 도착 좌표 둘 다 NULL 또는 둘 다 NOT NULL | 도착지 없으면 이름·주소 NULL, 마지막 이동 0 |
| 이동수단·대분류·path_type 허용 값 CHECK | 경로 구간 수·연속성·장소 좌표와의 연결 |
| 경로 순서 CHECK, JSON 배열·최소 2개 CHECK | 출발 시각 30분 단위, 추천 버전 데이터 완전성 |

DDL은 CHECK 제약이 강제되는 MySQL 8.0.16 이상을 전제로 한다. 행 여러 개에 걸친 합계·연속성은 CHECK만으로 보장하지 않는다.

스냅샷은 저장 이후 수정하지 않는다. JPA 연관관계에 공용 장소 삭제 CASCADE를 지정하지 않는다. 회원 탈퇴 시 저장 코스를 삭제하는 처리는 회원 서비스와 저장 서비스의 계약으로 별도 구현한다.

## 6. 저장·조회 흐름

1. 인증 회원 확인 → 기존 `(회원, 추천 버전)` 저장본 조회.
2. 기존 저장본이 없으면 추천 서비스로 선택 버전의 접근 권한·만료·내용 확인.
3. 장소·시간·경로를 검증한 뒤 `course`, `course_place`, `course_route_leg`를 한 트랜잭션으로 저장. 하나라도 실패하면 모두 취소.
4. 목록·상세·지도는 세 저장 테이블로 응답한다. 공용 장소·카테고리를 JOIN해 표시 값을 갱신하지 않는다.
5. 최신 조회만 본인 소유 확인 후 `place_id`를 장소 서비스로 전달한다. 그 결과를 스냅샷에 UPDATE하지 않는다.
6. 코스를 삭제하면 장소·경로 하위 행도 함께 삭제된다. 공용 장소는 유지한다.

추천 서비스는 최소한 위 API 대응표의 전체 데이터를 같은 불변 버전에서 제공해야 한다. 실제 메서드 이름·ID 최대 길이·접근 검증·경로 제공 범위는 나혜빈 담당과 확인할 연동 항목이다.

## 7. 기존 데이터 전환

- 현재 저장 API·엔티티는 미구현이지만, 로컬 DB에 수동 생성한 데이터가 없는지는 별도 확인해야 한다.
- 비어 있는 DB라면 확인 후 새 DDL로 생성한다. 기존 테이블 삭제를 자동 실행하지 않는다.
- 기존 저장 데이터가 있으면 현재 장소 정보로 과거 스냅샷을 채우지 않는다. 복원 가능한 원본 추천 버전·경로가 있는지 먼저 조사한다.
- 복원 불가능한 기존 행은 삭제·별도 보관·이전 데이터 표시 중 처리 방침을 확인한 뒤 마이그레이션을 설계한다. 원본 추천 ID를 임의로 만들어 채우지 않는다.
- 기존 데이터 처리와 중복 여부를 확인한 뒤 NOT NULL·UNIQUE를 적용한다. 이 검토안에는 실행용 ALTER나 삭제 SQL을 넣지 않는다.

## 8. 검토할 결정

1. `course_place`에 장소 값을 복사하고 구간 테이블 + JSON 좌표로 경로를 저장하는 방식.
2. ID 128자·대소문자 구분, 코스명 810자, 장소명 200자 등 필드 길이가 추천 데이터 계약과 맞는지.
3. 세분류 미확인 후보의 저장 응답, 참조 중인 공용 장소의 물리 삭제 제한.
4. 추천의 경로 제공 범위·좌표 오차·데이터 크기 상한과 외부 데이터 보관 범위.

`erd.md`의 신규 생성용 DDL·삭제 순서·남은 항목과 `course-api.md`의 관련 문구를 함께 갱신했다. 기존 DB의 ALTER·데이터 전환은 별도 작업이다. PR 리뷰에서 추천·장소·지도 연동 항목을 확인하며, 문서 반영만으로 팀 합의나 실제 DB 적용이 완료된 것으로 보지 않는다.
