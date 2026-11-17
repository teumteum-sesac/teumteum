package com.teumteum.recommend.service;

import com.teumteum.recommend.client.KakaoMobilityClient;
import com.teumteum.recommend.client.OdsayClient;
import com.teumteum.recommend.client.OrsClient;
import com.teumteum.recommend.domain.TransportType;
import com.teumteum.recommend.domain.TravelRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 두 지점 사이 이동시간을 구하는 서비스 (REQ-022, NFR-025).
 * <p>
 * 1. 고른 이동수단에 맞는 API 하나만 부른다
 *    CAR → 카카오모빌리티 · WALK → OpenRouteService · TRANSIT → ODsay
 * 2. API 가 실패하면(Optional.empty) 오류로 끝내지 않고 직선거리로 추정한다 → estimated true
 * <p>
 * 코스 구성·추천·장소 교체는 클라이언트를 직접 부르지 않고 이 서비스만 부른다.
 * 그래서 "어떤 API 를 부를지, 실패하면 어떻게 할지" 규칙이 이 한 곳에만 있다.
 * <p>
 * ODsay 무료 한도 때문에 최종 후보 코스에만 대중교통 API 를 부르는 규칙(NFR-014)은
 * 이 서비스를 부르는 쪽(코스 구성)에서 지킨다. 후보를 고를 때는 StraightLineTravelCalculator 로 어림잡으면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTimeService {

    private final KakaoMobilityClient kakaoMobilityClient;
    private final OrsClient orsClient;
    private final OdsayClient odsayClient;
    private final StraightLineTravelCalculator straightLineTravelCalculator;

    /**
     * 두 지점 사이 이동 결과를 구한다. 항상 결과를 돌려준다 (실패해도 직선거리 추정값).
     *
     * @param transportType 이동수단 (TRANSIT / WALK / CAR)
     * @return API 성공 시 API 결과(estimated false), 실패 시 직선거리 추정 결과(estimated true)
     */
    public TravelRoute findRoute(TransportType transportType,
                                 double fromLatitude, double fromLongitude,
                                 double toLatitude, double toLongitude) {
        Optional<TravelRoute> route = callApi(transportType, fromLatitude, fromLongitude, toLatitude, toLongitude);

        // orElseGet: 값이 없을 때만 괄호 안 코드를 실행한다 (성공하면 직선거리 계산을 아예 안 함)
        return route.orElseGet(() ->
                estimateByStraightLine(transportType, fromLatitude, fromLongitude, toLatitude, toLongitude));
    }

    /**
     * 이동수단에 맞는 API 하나만 부른다.
     * switch 식이라 TransportType 에 값이 추가되면 여기서 컴파일 오류가 나서 빠뜨리지 않는다.
     */
    private Optional<TravelRoute> callApi(TransportType transportType,
                                          double fromLatitude, double fromLongitude,
                                          double toLatitude, double toLongitude) {
        return switch (transportType) {
            case CAR -> kakaoMobilityClient.findCarRoute(fromLatitude, fromLongitude, toLatitude, toLongitude);
            case WALK -> orsClient.findWalkRoute(fromLatitude, fromLongitude, toLatitude, toLongitude);
            case TRANSIT -> odsayClient.findTransitRoute(fromLatitude, fromLongitude, toLatitude, toLongitude);
        };
    }

    /**
     * API 가 실패했을 때 직선거리로 이동시간을 추정한다.
     * 실패 원인(키 오류·타임아웃 등)은 각 클라이언트가 이미 로그로 남겼으므로 여기서는 대체했다는 사실만 남긴다.
     */
    private TravelRoute estimateByStraightLine(TransportType transportType,
                                               double fromLatitude, double fromLongitude,
                                               double toLatitude, double toLongitude) {
        int travelMinutes = straightLineTravelCalculator.calculateTravelMinutes(
                transportType, fromLatitude, fromLongitude, toLatitude, toLongitude);
        log.info("{} 이동시간 API 실패 → 직선거리 추정 {}분으로 대체", transportType, travelMinutes);
        return TravelRoute.estimated(travelMinutes);
    }
}
