package com.teumteum.recommend.client;

import com.teumteum.recommend.domain.TravelRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 이동시간 외부 API 3종을 실제 서버로 한 번씩 불러 보는 확인용 테스트.
 * <p>
 * 단위 테스트(XxxClientTest)는 가짜 응답으로 "변환" 만 확인한다.
 * 이 테스트는 그 앞단, 즉 우리 코드가 만든 요청(주소·좌표 순서·키 위치·키 인코딩)을 실제 서버가 받아 주는지 확인한다.
 * <p>
 * 평소(gradlew test, 팀원 컴퓨터, CI)에는 키 환경변수가 없어서 전부 건너뛴다(skipped).
 * 실제 호출은 키가 필요하고, 무료 한도를 쓰고, ODsay 는 등록된 IP 에서만 되고, 결과가 매번 달라질 수 있기 때문이다.
 * <p>
 * 실행 방법 (backend 폴더, PowerShell) — 필요한 키만 넣으면 그 테스트만 실행된다
 * <pre>
 * $env:KAKAO_REST_API_KEY="..."; $env:ORS_API_KEY="..."; $env:ODSAY_API_KEY="..."
 * .\gradlew.bat test --tests "*TravelClientLiveTest"
 * </pre>
 * 결과(분·노선)는 build/reports/tests/test/index.html 의 Standard output 에서 볼 수 있다.
 * 키는 터미널에만 넣고 코드·커밋에 남기지 않는다.
 */
class TravelClientLiveTest {

    // 성수역 → 서울숲 (차량·도보, 약 1.7km)
    private static final double SEONGSU_LAT = 37.544556;
    private static final double SEONGSU_LNG = 127.055879;
    private static final double SEOUL_FOREST_LAT = 37.544403;
    private static final double SEOUL_FOREST_LNG = 127.037398;

    // 잠실역 → 낙성대역 (대중교통. ODsay 는 출발·도착이 너무 가까우면 경로를 주지 않아 먼 구간을 쓴다)
    private static final double JAMSIL_LAT = 37.513841;
    private static final double JAMSIL_LNG = 127.101823;
    private static final double NAKSEONGDAE_LAT = 37.476813;
    private static final double NAKSEONGDAE_LNG = 126.964156;

    @Test
    @EnabledIfEnvironmentVariable(named = "KAKAO_REST_API_KEY", matches = ".+")
    @DisplayName("[실제 호출] 카카오모빌리티 차량: 성수역 → 서울숲")
    void kakaoMobility_car() {
        KakaoMobilityClient client = new KakaoMobilityClient(System.getenv("KAKAO_REST_API_KEY"));

        Optional<TravelRoute> result = client.findCarRoute(SEONGSU_LAT, SEONGSU_LNG, SEOUL_FOREST_LAT, SEOUL_FOREST_LNG);

        assertTrue(result.isPresent(), "차량 경로를 받지 못함 — 키·요청 주소를 확인");
        print("차량", result.get());
        assertTrue(result.get().travelMinutes() > 0);
        assertFalse(result.get().coordinates().isEmpty(), "차량은 경로 좌표가 있어야 함");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ORS_API_KEY", matches = ".+")
    @DisplayName("[실제 호출] OpenRouteService 도보: 성수역 → 서울숲")
    void ors_walk() {
        OrsClient client = new OrsClient(System.getenv("ORS_API_KEY"));

        Optional<TravelRoute> result = client.findWalkRoute(SEONGSU_LAT, SEONGSU_LNG, SEOUL_FOREST_LAT, SEOUL_FOREST_LNG);

        assertTrue(result.isPresent(), "도보 경로를 받지 못함 — 키·요청 주소를 확인");
        print("도보", result.get());
        assertTrue(result.get().travelMinutes() > 0);
        assertFalse(result.get().coordinates().isEmpty(), "도보는 경로 좌표가 있어야 함");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ODSAY_API_KEY", matches = ".+")
    @DisplayName("[실제 호출] ODsay 대중교통: 잠실역 → 낙성대역")
    void odsay_transit() {
        OdsayClient client = new OdsayClient(System.getenv("ODSAY_API_KEY"));

        Optional<TravelRoute> result = client.findTransitRoute(JAMSIL_LAT, JAMSIL_LNG, NAKSEONGDAE_LAT, NAKSEONGDAE_LNG);

        assertTrue(result.isPresent(), "대중교통 경로를 받지 못함 — 키 인코딩·등록 IP 를 확인");
        print("대중교통", result.get());
        assertTrue(result.get().travelMinutes() > 0);
        assertNotNull(result.get().transitInfo(), "대중교통은 노선·환승 문장이 있어야 함");
    }

    /** 사람이 눈으로 확인할 수 있게 결과를 출력한다 (값은 매번 달라질 수 있어 검사하지 않음) */
    private void print(String label, TravelRoute route) {
        System.out.printf("[%s] %d분, 좌표 %d개, transitInfo=%s%n",
                label, route.travelMinutes(), route.coordinates().size(), route.transitInfo());
    }
}
