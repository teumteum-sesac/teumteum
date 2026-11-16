package com.teumteum.recommend.dto;

/**
 * 응답에 담는 위치 1곳 (출발지 또는 도착지).
 * <pre>
 * { "name": "성수역", "address": null, "latitude": 37.5446, "longitude": 127.0559 }
 * </pre>
 * 모양은 LocationRequest 와 같지만, 응답은 서버가 만들어 내보내는 값이라 검증 어노테이션이 없다.
 * 요청·응답을 같은 클래스로 쓰지 않는 이유: 한쪽만 바꿔야 할 때(예: 응답에만 필드 추가) 서로 영향을 주지 않게 하려고.
 */
public record LocationResponse(
        String name,       // 장소 이름, 없으면 null
        String address,    // 주소, 없으면 null
        double latitude,   // 위도
        double longitude   // 경도
) {
}
