package com.teumteum.course.dto;

import java.math.BigDecimal;

// 저장한 코스의 출발지 또는 도착지 한 곳을 프론트에 전달하는 DTO다.
// 전체 상세 응답의 origin과 destination에서 같은 타입을 사용한다.
// 도착지를 설정하지 않았다면 빈 위치 객체를 만들지 않고 destination 자체를 null로 전달한다.
// API 명세는 name/address/latitude/longitude를 같은 단계의 필드로 정의하므로 좌표 DTO를 중첩하지 않는다.
// Service에서 Course 엔티티의 origin... 또는 destination... 필드를 꺼내 생성자에 전달한다.
// record가 생성자와 name(), address(), latitude(), longitude() 접근자 등을 자동 제공한다.
public record CourseLocationResponse(
        // 위치 이름. 예: "우리 집". 없으면 null이며 화면에서 기본 문구를 표시한다.
        String name,
        // 주소. 제공되지 않으면 null이다.
        String address,
        // 위도(남북 위치). 위치 객체가 있으면 좌표는 필수이며 유효 범위는 -90~90이다.
        BigDecimal latitude,
        // 경도(동서 위치). 유효 범위는 -180~180이다.
        // 주석으로 범위를 적는 것만으로 자동 검증되지는 않는다.
        BigDecimal longitude
) {
}
