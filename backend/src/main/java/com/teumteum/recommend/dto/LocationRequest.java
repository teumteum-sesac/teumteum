package com.teumteum.recommend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 추천 요청의 위치 1곳 (출발지 또는 도착지).
 * <pre>
 * { "name": "성수역", "address": "서울 성동구 아차산로 100", "latitude": 37.5446, "longitude": 127.0559 }
 * </pre>
 * - name, address : 화면 표시용. 현재 위치로 출발하면 없을 수 있어 null 허용
 * - latitude      : 위도, 필수, -90 ~ 90
 * - longitude     : 경도, 필수, -180 ~ 180
 * 위도·경도는 값이 빠졌을 때 0.0 이 아니라 null 이 되도록 Double 을 쓴다. (그래야 @NotNull 로 잡힌다)
 * 검증은 컨트롤러의 @Valid 에서 동작한다.
 */
public record LocationRequest(
        String name,
        String address,
        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,
        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude
) {

}
