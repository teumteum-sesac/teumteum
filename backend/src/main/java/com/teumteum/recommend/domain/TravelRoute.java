package com.teumteum.recommend.domain;

import java.util.List;

/**
 * 두 지점 사이 이동 결과 1구간 (우리 쪽 결과 모양).
 * <p>
 * 차량(카카오모빌리티)·도보(OpenRouteService)·대중교통(ODsay)은 응답 JSON 모양이 전부 다르다.
 * 각 클라이언트가 자기 응답을 이 TravelRoute 로 바꿔서 돌려주면,
 * 이동시간 서비스(3-5)는 이동수단과 상관없이 똑같이 다룰 수 있다.
 * <p>
 * 예) 성수역 → 서울숲 차량: travelMinutes 12, coordinates [ (37.5446, 127.0559), ... , (37.5444, 127.0365) ], transitInfo null
 * <br>
 * 예) 잠실역 → 낙성대역 대중교통: travelMinutes 25, coordinates [], transitInfo "수도권 2호선 · 환승 0회"
 *
 * @param travelMinutes 이동시간(분). API 가 초로 주면 올림해서 분으로 바꾼 값
 * @param coordinates   지도에 그릴 경로 좌표, 진행 방향 순서. 경로가 없으면 빈 목록.
 *                      대중교통은 1차 버전에서 장소 사이를 직선으로 그리므로 항상 빈 목록
 * @param transitInfo   대중교통 노선·환승 문장 (예: "버스 3411 → 버스 461 · 환승 1회", REQ-025).
 *                      차량·도보는 null
 */
public record TravelRoute(
        int travelMinutes,
        List<Coordinate> coordinates,
        String transitInfo
) {

    /**
     * 차량·도보용 생성자. 노선 정보가 없으니 transitInfo 는 null 로 채운다.
     * <p>
     * record 에 직접 추가한 생성자는 첫 줄에서 반드시 this(...) 로 원래 생성자(필드 3개짜리)를 불러야 한다.
     * 이 생성자 덕분에 기존 KakaoMobilityClient·OrsClient 의 new TravelRoute(분, 좌표) 코드를 고치지 않아도 된다.
     */
    public TravelRoute(int travelMinutes, List<Coordinate> coordinates) {
        this(travelMinutes, coordinates, null);
    }

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
