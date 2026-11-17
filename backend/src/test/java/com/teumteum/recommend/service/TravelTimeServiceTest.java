package com.teumteum.recommend.service;

import com.teumteum.recommend.client.KakaoMobilityClient;
import com.teumteum.recommend.client.OdsayClient;
import com.teumteum.recommend.client.OrsClient;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import com.teumteum.recommend.domain.TravelRoute.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이동시간 서비스 테스트.
 * <p>
 * 클라이언트 3개는 Mockito 가짜(@Mock)로 바꿔서 실제 API 를 부르지 않는다.
 * 가짜에게 "이렇게 물으면 이렇게 답해" 를 정해 두고(given), 서비스가 맞는 클라이언트를 불렀는지(verify) 확인한다.
 * 직선거리 계산기는 순수 계산이라 진짜 객체(@Spy)를 그대로 쓴다.
 * <p>
 * 기준 값: 위도 0.01도(약 1.11km) 차이 → 직선거리 추정 도보 22분 · 대중교통 6분 · 차량 5분
 * (StraightLineTravelCalculatorTest 와 같은 좌표)
 */
@ExtendWith(MockitoExtension.class)
class TravelTimeServiceTest {

    @Mock
    private KakaoMobilityClient kakaoMobilityClient;
    @Mock
    private OrsClient orsClient;
    @Mock
    private OdsayClient odsayClient;
    @Spy
    private StraightLineTravelCalculator straightLineTravelCalculator;

    /** 위의 가짜·진짜 객체 4개를 생성자에 넣어 서비스를 만들어 준다 */
    @InjectMocks
    private TravelTimeService travelTimeService;

    private static final double FROM_LAT = 37.5446;
    private static final double FROM_LNG = 127.0559;
    private static final double TO_LAT = FROM_LAT + 0.01;
    private static final double TO_LNG = FROM_LNG;

    @Test
    @DisplayName("차량은 카카오모빌리티만 부르고, 성공하면 그 결과를 그대로 돌려준다")
    void findRoute_car_usesKakaoMobility() {
        TravelRoute carRoute = new TravelRoute(11, List.of(new Coordinate(FROM_LAT, FROM_LNG)));
        given(kakaoMobilityClient.findCarRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG)).willReturn(Optional.of(carRoute));

        TravelRoute result = travelTimeService.findRoute(TransportType.CAR, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertSame(carRoute, result);
        assertFalse(result.estimated());
        verify(orsClient, never()).findWalkRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(odsayClient, never()).findTransitRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("도보는 OpenRouteService 만 부른다")
    void findRoute_walk_usesOrs() {
        TravelRoute walkRoute = new TravelRoute(26, List.of(new Coordinate(FROM_LAT, FROM_LNG)));
        given(orsClient.findWalkRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG)).willReturn(Optional.of(walkRoute));

        TravelRoute result = travelTimeService.findRoute(TransportType.WALK, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertSame(walkRoute, result);
        verify(kakaoMobilityClient, never()).findCarRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(odsayClient, never()).findTransitRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("대중교통은 ODsay 만 부르고, 노선 정보도 그대로 전달된다")
    void findRoute_transit_usesOdsay() {
        TravelRoute transitRoute = new TravelRoute(25, List.of(), "수도권 2호선 · 환승 0회");
        given(odsayClient.findTransitRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG)).willReturn(Optional.of(transitRoute));

        TravelRoute result = travelTimeService.findRoute(TransportType.TRANSIT, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertEquals("수도권 2호선 · 환승 0회", result.transitInfo());
        assertFalse(result.estimated());
        verify(kakaoMobilityClient, never()).findCarRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(orsClient, never()).findWalkRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("API 가 성공하면 직선거리 계산은 하지 않는다")
    void findRoute_doesNotEstimate_whenApiSucceeds() {
        given(orsClient.findWalkRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG))
                .willReturn(Optional.of(new TravelRoute(26, List.of())));

        travelTimeService.findRoute(TransportType.WALK, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        verify(straightLineTravelCalculator, never())
                .calculateTravelMinutes(TransportType.WALK, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);
    }

    @Test
    @DisplayName("도보 API 가 실패하면 직선거리 22분으로 추정하고 estimated 가 true 다")
    void findRoute_walkFails_estimatesByStraightLine() {
        given(orsClient.findWalkRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG)).willReturn(Optional.empty());

        TravelRoute result = travelTimeService.findRoute(TransportType.WALK, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertEquals(22, result.travelMinutes());
        assertTrue(result.estimated());
        assertTrue(result.coordinates().isEmpty()); // 지도는 직선으로 그린다
        assertNull(result.transitInfo());
    }

    @Test
    @DisplayName("차량 API 가 실패하면 차량 속도로 5분을 추정한다")
    void findRoute_carFails_usesCarSpeed() {
        given(kakaoMobilityClient.findCarRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG)).willReturn(Optional.empty());

        TravelRoute result = travelTimeService.findRoute(TransportType.CAR, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertEquals(5, result.travelMinutes());
        assertTrue(result.estimated());
    }

    @Test
    @DisplayName("대중교통 API 가 실패하면 대중교통 속도로 6분을 추정하고 노선 정보는 없다")
    void findRoute_transitFails_usesTransitSpeed() {
        given(odsayClient.findTransitRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG)).willReturn(Optional.empty());

        TravelRoute result = travelTimeService.findRoute(TransportType.TRANSIT, FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertEquals(6, result.travelMinutes());
        assertTrue(result.estimated());
        assertNull(result.transitInfo());
    }
}
