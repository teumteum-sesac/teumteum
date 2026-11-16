package com.teumteum.recommend.domain;

import java.util.List;

/**
 * 두 지점 사이 이동 결과 1구간 (우리 쪽 결과 모양).
 * <p>
 * 차량(카카오모빌리티)·도보(OpenRouteService)·대중교통(ODsay)은 응답 JSON 모양이 전부 다르다.
 * 각 클라이언트가 자기 응답을 이 TravelRoute 로 바꿔서 돌려주면,
 * 이동시간 서비스(3-5)는 이동수단과 상관없이 똑같이 다룰 수 있다.
 * <p>
 * 예) 성수역 → 서울숲 차량: travelMinutes 12, coordinates [ (37.5446, 127.0559), ... , (37.5444, 127.0365) ]
 *
 * @param travelMinutes 이동시간(분). API 가 초로 주면 올림해서 분으로 바꾼 값
 * @param coordinates   지도에 그릴 경로 좌표, 진행 방향 순서. 경로가 없으면 빈 목록
 */
public record TravelRoute(
        int travelMinutes,
        List<Coordinate> coordinates
) {

    /**
     * 위도·경도 한 점.
     * 외부 API 마다 순서가 다르다 (카카오모빌리티는 경도, 위도 순).
     * 우리 코드 안에서는 항상 이름(latitude, longitude)으로 다뤄서 순서 실수를 막는다.
     */
    public record Coordinate(
            double latitude,   // 위도 (y)
            double longitude   // 경도 (x)
    ) {
    }
}
