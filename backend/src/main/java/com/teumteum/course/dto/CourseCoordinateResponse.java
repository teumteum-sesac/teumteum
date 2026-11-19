package com.teumteum.course.dto;

import java.math.BigDecimal;

// 프론트에 전달할 좌표 한 개(위도·경도 한 쌍)를 담는 응답 DTO다.
// 경로 한 구간은 이 DTO 여러 개를 List<CourseCoordinateResponse>로 묶어 좌표 목록을 표현한다.
// BigDecimal은 십진수를 정확하게 표현하는 타입이며, 저장된 경로 좌표의 타입과 맞췄다.
// record는 아래 두 값을 받는 생성자, latitude()/longitude() 접근자,
// 값 기준 equals()/hashCode(), toString()을 자동으로 제공한다. Lombok은 필요 없다.
// 선언만으로 DB 값을 가져오지는 않는다. 조회한 엔티티의 좌표 값을 전달해 DTO 객체를 만들어야 한다.
public record CourseCoordinateResponse(
        // 위도. 예: 37.544. 생성자의 첫 번째 인수로 전달하며 latitude()로 읽는다.
        BigDecimal latitude,
        // 경도. 예: 127.055. 생성자의 두 번째 인수로 전달하며 longitude()로 읽는다.
        BigDecimal longitude
) {
    // 추가 동작이 없어 본문은 비워둔다. 각 구성요소는 생성 후 다른 값으로 재대입할 수 없다.
}
