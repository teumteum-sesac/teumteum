package com.teumteum.course.dto;

import java.math.BigDecimal;

// 저장 코스에 포함된 장소 한 곳의 정보와 일정을 전달하는 응답 DTO다.
// 전체 코스 상세 응답에서는 List<CoursePlaceResponse>로 묶어 방문 순서대로 제공한다.
// 최신 공용 장소 정보가 아니라 CoursePlace 엔티티에 보관한 저장 당시 정보를 사용한다.
// 아래 필드명과 타입은 docs/course-api.md의 CoursePlaceResponse 정의를 따른다.
public record CoursePlaceResponse(
        // 저장 장소 행의 ID. 엔티티의 Long을 Service에서 문자열로 변환한다.
        String coursePlaceId,
        // 공용 장소의 ID. 같은 장소라도 코스마다 coursePlaceId는 달라질 수 있다.
        String placeId,
        // 코스 안의 방문 순서(1부터). 엔티티의 Short는 Service에서 Integer로 변환한다.
        Integer visitOrder,
        // 저장 당시 장소명.
        String placeName,
        // 저장 당시 대분류 코드. 엔티티의 categoryCode를 이 응답 필드에 넣는다.
        String category,
        // 저장 당시 세분류 이름. 엔티티의 subCategoryName에 대응한다.
        String subCategory,
        // 저장 당시 주소. 정보가 없으면 null이다.
        String address,
        // 저장 당시 위도·경도. 좌표 DTO로 중첩하지 않고 API 명세대로 직접 노출한다.
        BigDecimal latitude,
        BigDecimal longitude,
        // 사진 URL·소개·운영시간은 각각 정보가 없으면 null이다.
        String imageUrl,
        String description,
        String openingHours,
        // 해당 장소의 예상 체류시간(분).
        Integer stayMinutes,
        // 직전 지점에서 이 장소까지의 이동시간(분). 첫 장소는 출발지에서 오는 시간이다.
        Integer travelMinutes,
        // 대중교통 노선·환승 설명. 정보가 없으면 null이다.
        String transitInfo
) {
}
