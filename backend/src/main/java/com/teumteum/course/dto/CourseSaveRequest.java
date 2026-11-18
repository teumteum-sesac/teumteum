package com.teumteum.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /api/courses에서 프론트가 보내는 코스 저장 요청을 받는다.
// 장소·일정·경로는 요청으로 받지 않고, Service가 추천 ID로 추천 서비스에서 가져온다.
// 저장할 회원 ID는 요청이 아닌 로그인 인증 정보에서 확인한다.
public record CourseSaveRequest(
    // 저장하려는 추천 코스 한 건의 ID. 저장 후 DB에서 생성되는 courseId와는 다르다.
    @NotBlank // null, 빈 문자열, 공백만 있는 값은 허용하지 않는다.
    @Size(max = 128) // 저장 구조의 추천 ID 최대 길이에 맞춰 128자까지 허용한다.
    String recommendationId
) {
}


