package com.teumteum.recommend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * ODsay 대중교통 길찾기 API(GET /v1/api/searchPubTransPathT) 응답을 그대로 받는 그릇.
 * <p>
 * 실제로는 정류장 목록(passStopList), 요금(payment), 출구 좌표 등 훨씬 많이 오지만 우리가 쓰는 칸만 적는다.
 * <pre>
 * result
 *  └─ path[]                          경로 후보 여러 개. ODsay 추천 순서라 path[0] 을 쓴다
 *     ├─ info
 *     │   ├─ totalTime                총 소요시간(분). 차량·도보와 달리 이미 분 단위
 *     │   ├─ busTransitCount          버스 "탑승" 횟수 (환승 횟수 아님!)
 *     │   └─ subwayTransitCount       지하철 "탑승" 횟수
 *     └─ subPath[]                    구간 순서대로. trafficType 1 지하철 · 2 버스 · 3 도보
 *         ├─ lane[]                   이 구간에서 탈 수 있는 노선. 여러 개면 "아무거나 타도 됨" 이라 lane[0] 만 쓴다
 *         │   ├─ name                 지하철 노선 이름 (예: "수도권 2호선")
 *         │   └─ busNo                버스 번호 (예: "3411")
 *         ├─ startX·startY·endX·endY  탄 곳·내린 곳 좌표 (지하철·버스만)
 *         └─ passStopList.stations[]  지나는 정류장 x(경도)·y(위도) 문자열 (지하철·버스만) → 지도 경로선
 * </pre>
 * 카카오·ORS 와 달리 실패해도 HTTP 200 으로 오고, result 대신 error 칸이 온다.
 * 예) 키 인증 실패: {"error":[{"code":"500","message":"[ApiKeyAuthFailed] ..."}]}
 * 예) 700m 이내:      {"error":{"msg":"출, 도착지가 700m이내입니다.","code":"-98"}}
 * 예) 너무 자주 호출:  {"error":[{"code":"429","message":"Too Many Requests"}]}
 * error 는 모양이 일정하지 않아(배열·객체) Object 로 받아 로그에만 남기고, 실패 판단은 "result 가 없으면 실패" 로 한다.
 * 이 클래스는 client 패키지 안에서만 쓰고, 바깥에는 TravelRoute 로 바꿔서 내보낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OdsayDirectionsResponse(
        Result result,
        Object error   // 실패 이유 (로그용). Object 라서 배열이든 객체든 그대로 받는다
) {

    /** 성공 응답처럼 error 없이 만드는 생성자 (기존 테스트의 new OdsayDirectionsResponse(result) 유지) */
    public OdsayDirectionsResponse(Result result) {
        this(result, null);
    }

    /** 검색 결과. 실패 응답이면 이 칸이 통째로 없다(null) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            List<Path> path
    ) {
    }

    /** 경로 후보 1개 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Path(
            int pathType,          // 1 지하철 · 2 버스 · 3 버스+지하철
            Info info,
            List<SubPath> subPath
    ) {
    }

    /** 경로 전체 요약 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            int totalTime,          // 총 소요시간(분)
            int busTransitCount,    // 버스 탑승 횟수
            int subwayTransitCount  // 지하철 탑승 횟수
    ) {
    }

    /**
     * 구간 1개 (도보 → 지하철 → 도보 처럼 순서대로).
     * 지하철·버스 구간은 탄 곳·내린 곳 좌표(startX·startY·endX·endY)와 지나는 정류장(passStopList)이 온다.
     * 도보 구간은 좌표가 오지 않는다(null). 좌표는 X = 경도, Y = 위도
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubPath(
            int trafficType,           // 1 지하철 · 2 버스 · 3 도보
            int sectionTime,           // 이 구간 소요시간(분)
            List<Lane> lane,           // 도보 구간은 없음(null)
            Double startX,             // 탄 곳 경도
            Double startY,             // 탄 곳 위도
            Double endX,               // 내린 곳 경도
            Double endY,               // 내린 곳 위도
            PassStopList passStopList  // 지나는 정류장 (탄 곳·내린 곳 포함, 순서대로)
    ) {

        /** 좌표 없이 만드는 생성자 (기존 테스트의 new SubPath(종류, 분, 노선) 유지) */
        public SubPath(int trafficType, int sectionTime, List<Lane> lane) {
            this(trafficType, sectionTime, lane, null, null, null, null, null);
        }
    }

    /** 지나는 정류장 목록 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PassStopList(
            List<Station> stations
    ) {
    }

    /** 정류장(역) 1곳. ODsay 는 좌표를 문자열로 보낸다 (예: "127.0473") */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Station(
            String x,  // 경도
            String y   // 위도
    ) {
    }

    /** 노선 1개. 지하철이면 name, 버스면 busNo 만 채워져 온다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lane(
            String name,  // 지하철 노선 이름
            String busNo  // 버스 번호
    ) {
    }
}
