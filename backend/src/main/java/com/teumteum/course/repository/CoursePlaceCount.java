package com.teumteum.course.repository;

// DB에서 조회한 코스 ID와 장소 수를 받는 조회 결과 타입이다.
// 프론트에 전달하는 응답 DTO와는 별개다.
public interface CoursePlaceCount {

    Long getCourseId();

    Long getPlaceCount();
}