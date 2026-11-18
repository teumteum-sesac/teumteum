package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.TransportType;
import org.springframework.stereotype.Component;

/**
 * 직선거리로 이동시간(분)을 추정하는 계산기 (REQ-022, NFR-025).
 * <p>
 * 이동시간 API(카카오모빌리티·OpenRouteService·ODsay)가 실패했을 때 대신 쓴다.
 * 이 값으로 계산한 구간이 있으면 코스의 estimated 가 true 가 되고 화면에 "예상 시간"으로 표시된다.
 * <p>
 * 계산 순서
 * 1. 하버사인 공식으로 두 좌표 사이 직선거리(km)를 구한다
 * 2. 실제 길은 직선보다 돌아가므로 우회 보정(1.3배)을 곱한다
 * 3. 이동수단별 평균 속도로 나눠 분으로 바꾸고, 올림해서 정수로 만든다
 *    (적게 잡으면 이용 시간을 넘는 코스가 나올 수 있어서 올림)
 * <p>
 * 외부 API·DB 를 쓰지 않는 순수 계산이라 단위 테스트로 검증한다.
 */
@Component
public class StraightLineTravelCalculator {

    /** 지구 평균 반지름(km). 하버사인 공식에 쓰는 값 */
    static final double EARTH_RADIUS_KM = 6371.0;

    // 아래 우회 보정·속도는 직선거리 추정의 팀 기준이다 (2026-11-16 확정, docs/recommend-api.md 시간 계산 규칙).
    // 실측과 크게 다르면 이 상수만 고치면 된다.

    /** 직선 → 실제 길 우회 보정 */
    static final double DETOUR_FACTOR = 1.3;

    /** 이동수단별 평균 속도(km/h) */
    static final double WALK_SPEED_KMH = 4.0;     // 보통 걷는 속도
    static final double TRANSIT_SPEED_KMH = 15.0; // 대기·환승 포함 시내 평균
    static final double CAR_SPEED_KMH = 20.0;     // 서울 시내 신호·정체 감안

    /**
     * 대중교통이어도 이 거리(직선, km) 안이면 걸어간다고 본다.
     * ODsay 가 출발·도착이 700m 이내면 계산하지 않기 때문에(오류 -98) 같은 기준을 쓴다. 실제로도 걷는 거리다.
     */
    static final double TRANSIT_WALK_DISTANCE_KM = 0.7;

    /**
     * 두 좌표 사이 이동시간(분)을 직선거리로 추정한다.
     * 대중교통이라도 700m 이내면 걷는 속도로 계산한다 (실제 계산인 TravelTimeService 도 이 구간은 도보로 계산).
     * → 코스를 고를 때의 어림값과 확정할 때의 실제값이 같은 기준이 된다.
     *
     * @param transportType 이동수단 (TRANSIT / WALK / CAR)
     * @return 올림한 이동시간(분). 같은 좌표면 0
     */
    public int calculateTravelMinutes(TransportType transportType,
                                      double fromLatitude, double fromLongitude,
                                      double toLatitude, double toLongitude) {
        double distanceKm = calculateDistanceKm(fromLatitude, fromLongitude, toLatitude, toLongitude);
        double speed = isTransitWalkingDistance(transportType, distanceKm) ? WALK_SPEED_KMH : speedOf(transportType);
        double roadDistanceKm = distanceKm * DETOUR_FACTOR;
        double hours = roadDistanceKm / speed;
        return (int) Math.ceil(hours * 60);
    }

    /**
     * 대중교통인데 걸어갈 거리(700m 이내)인지.
     * TravelTimeService 가 "ODsay 대신 도보 API 를 부를지" 정할 때도 이 메서드를 쓴다 (기준을 한 곳에 둔다).
     */
    public boolean isTransitWalkingDistance(TransportType transportType,
                                            double fromLatitude, double fromLongitude,
                                            double toLatitude, double toLongitude) {
        return isTransitWalkingDistance(transportType,
                calculateDistanceKm(fromLatitude, fromLongitude, toLatitude, toLongitude));
    }

    private boolean isTransitWalkingDistance(TransportType transportType, double distanceKm) {
        return transportType == TransportType.TRANSIT && distanceKm < TRANSIT_WALK_DISTANCE_KM;
    }

    /**
     * 하버사인 공식: 둥근 지구 위 두 점 사이의 최단(직선) 거리(km).
     * 위도·경도 차이를 피타고라스로 계산하면 경도 1도의 길이가 위도마다 달라서 틀린다.
     * 그래서 각도를 라디안으로 바꾼 뒤 구 위의 거리로 계산한다.
     */
    public double calculateDistanceKm(double fromLatitude, double fromLongitude,
                                      double toLatitude, double toLongitude) {
        double fromLat = Math.toRadians(fromLatitude);
        double toLat = Math.toRadians(toLatitude);
        double deltaLat = Math.toRadians(toLatitude - fromLatitude);
        double deltaLng = Math.toRadians(toLongitude - fromLongitude);

        // a: 두 점 사이 중심각의 절반에 대한 sin² 값 (하버사인 공식의 중간값)
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(fromLat) * Math.cos(toLat)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        // c: 지구 중심에서 본 두 점 사이 각도(라디안)
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c; // 각도 × 반지름 = 호의 길이
    }

    /**
     * 이동수단별 평균 속도(km/h).
     * switch 식은 enum 값을 하나라도 빠뜨리면 컴파일 오류가 나서, 나중에 이동수단이 늘어도 놓치지 않는다.
     */
    private double speedOf(TransportType transportType) {
        return switch (transportType) {
            case WALK -> WALK_SPEED_KMH;
            case TRANSIT -> TRANSIT_SPEED_KMH;
            case CAR -> CAR_SPEED_KMH;
        };
    }
}
