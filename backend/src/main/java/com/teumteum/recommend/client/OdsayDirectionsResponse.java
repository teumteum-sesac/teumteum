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
 *         └─ lane[]                   이 구간에서 탈 수 있는 노선. 여러 개면 "아무거나 타도 됨" 이라 lane[0] 만 쓴다
 *             ├─ name                 지하철 노선 이름 (예: "수도권 2호선")
 *             └─ busNo                버스 번호 (예: "3411")
 * </pre>
 * 카카오·ORS 와 달리 실패해도 HTTP 200 으로 오고, result 대신 error 칸이 온다.
 * 예) 키 인증 실패: {"error":[{"code":"500","message":"[ApiKeyAuthFailed] ..."}]}
 * error 는 모양이 일정하지 않아(배열·객체) 받지 않고, "result 가 없으면 실패" 로만 판단한다.
 * 이 클래스는 client 패키지 안에서만 쓰고, 바깥에는 TravelRoute 로 바꿔서 내보낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OdsayDirectionsResponse(
        Result result
) {

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

    /** 구간 1개 (도보 → 지하철 → 도보 처럼 순서대로) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubPath(
            int trafficType,  // 1 지하철 · 2 버스 · 3 도보
            int sectionTime,  // 이 구간 소요시간(분)
            List<Lane> lane   // 도보 구간은 없음(null)
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
