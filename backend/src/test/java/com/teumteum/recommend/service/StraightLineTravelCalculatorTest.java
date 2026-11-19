package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.TransportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 직선거리 이동시간 계산기 테스트.
 * <p>
 * 기준 값: 경도가 같고 위도만 다르면 거리 = 반지름 × 위도 차(라디안).
 * 위도 0.01도 ≈ 1.112km → 우회 1.3배 ≈ 1.4455km
 * - WALK    (4km/h)  : 21.68분 → 22분
 * - TRANSIT (15km/h) :  5.78분 →  6분
 * - CAR     (20km/h) :  4.34분 →  5분
 */
class StraightLineTravelCalculatorTest {

    private final StraightLineTravelCalculator calculator = new StraightLineTravelCalculator();

    // 성수역 근처 기준 좌표
    private static final double BASE_LAT = 37.5446;
    private static final double BASE_LNG = 127.0559;

    @Test
    @DisplayName("위도 1도 차이는 약 111.19km 이다")
    void calculateDistanceKm_returnsAbout111km_forOneDegreeLatitude() {
        double result = calculator.calculateDistanceKm(37.0, 127.0, 38.0, 127.0);

        assertEquals(111.19, result, 0.01);
    }

    @Test
    @DisplayName("출발과 도착을 바꿔도 거리는 같다")
    void calculateDistanceKm_isSymmetric() {
        double forward = calculator.calculateDistanceKm(BASE_LAT, BASE_LNG, 37.5665, 126.9780);
        double backward = calculator.calculateDistanceKm(37.5665, 126.9780, BASE_LAT, BASE_LNG);

        assertEquals(forward, backward, 0.000001);
    }

    @Test
    @DisplayName("같은 좌표면 이동시간은 0분이다")
    void calculateTravelMinutes_returnsZero_forSameLocation() {
        int result = calculator.calculateTravelMinutes(TransportType.WALK, BASE_LAT, BASE_LNG, BASE_LAT, BASE_LNG);

        assertEquals(0, result);
    }

    @Test
    @DisplayName("도보는 약 1.1km 직선거리에 22분이다")
    void calculateTravelMinutes_returns22_forWalk() {
        int result = calculator.calculateTravelMinutes(TransportType.WALK, BASE_LAT, BASE_LNG, BASE_LAT + 0.01, BASE_LNG);

        assertEquals(22, result);
    }

    @Test
    @DisplayName("대중교통은 약 1.1km 직선거리에 6분이다")
    void calculateTravelMinutes_returns6_forTransit() {
        int result = calculator.calculateTravelMinutes(TransportType.TRANSIT, BASE_LAT, BASE_LNG, BASE_LAT + 0.01, BASE_LNG);

        assertEquals(6, result);
    }

    @Test
    @DisplayName("차량은 약 1.1km 직선거리에 5분이다")
    void calculateTravelMinutes_returns5_forCar() {
        int result = calculator.calculateTravelMinutes(TransportType.CAR, BASE_LAT, BASE_LNG, BASE_LAT + 0.01, BASE_LNG);

        assertEquals(5, result);
    }

    @Test
    @DisplayName("아주 가까워도 0보다 멀면 올림해서 1분이다")
    void calculateTravelMinutes_roundsUpToOne_forVeryShortDistance() {
        // 위도 0.0001도 ≈ 11m → 도보 약 0.2분
        int result = calculator.calculateTravelMinutes(TransportType.WALK, BASE_LAT, BASE_LNG, BASE_LAT + 0.0001, BASE_LNG);

        assertEquals(1, result);
    }

    // ===================== 대중교통 700m 이내 = 도보 =====================

    @Test
    @DisplayName("대중교통이어도 700m 이내면 걷는 속도로 계산한다 (약 556m → 도보 10.8분 → 11분)")
    void calculateTravelMinutes_transitUsesWalkSpeed_within700m() {
        int transit = calculator.calculateTravelMinutes(TransportType.TRANSIT, BASE_LAT, BASE_LNG, BASE_LAT + 0.005, BASE_LNG);
        int walk = calculator.calculateTravelMinutes(TransportType.WALK, BASE_LAT, BASE_LNG, BASE_LAT + 0.005, BASE_LNG);

        assertEquals(11, transit);
        assertEquals(walk, transit);
    }

    @Test
    @DisplayName("대중교통 700m 넘으면 원래대로 대중교통 속도 (약 778m → 4.05분 → 5분)")
    void calculateTravelMinutes_transitUsesTransitSpeed_over700m() {
        int result = calculator.calculateTravelMinutes(TransportType.TRANSIT, BASE_LAT, BASE_LNG, BASE_LAT + 0.007, BASE_LNG);

        assertEquals(5, result);
    }

    @Test
    @DisplayName("걸어갈 거리 판단은 대중교통 + 700m 미만일 때만 true")
    void isTransitWalkingDistance() {
        assertTrue(calculator.isTransitWalkingDistance(TransportType.TRANSIT, BASE_LAT, BASE_LNG, BASE_LAT + 0.005, BASE_LNG));
        assertFalse(calculator.isTransitWalkingDistance(TransportType.TRANSIT, BASE_LAT, BASE_LNG, BASE_LAT + 0.007, BASE_LNG));
        assertFalse(calculator.isTransitWalkingDistance(TransportType.WALK, BASE_LAT, BASE_LNG, BASE_LAT + 0.005, BASE_LNG));
        assertFalse(calculator.isTransitWalkingDistance(TransportType.CAR, BASE_LAT, BASE_LNG, BASE_LAT + 0.005, BASE_LNG));
    }
}
