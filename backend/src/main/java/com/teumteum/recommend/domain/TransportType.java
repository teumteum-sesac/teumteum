package com.teumteum.recommend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이동수단과, 이동수단마다 다른 "너무 먼 곳" 기준.
 * <p>
 * 기준을 이동수단 enum 안에 두면 "도보는 몇 km 까지?" 를 찾으려고 여러 파일을 뒤질 필요가 없고,
 * 이동수단을 하나 더 만들 때 기준을 빠뜨릴 수 없다 (생성자 값이 없으면 컴파일 오류).
 * 숫자는 실제 추천 결과를 보면서 조정한다 (#170, 나혜빈 2026-11-19).
 * <p>
 * JSON 에는 여전히 "WALK" 같은 이름만 나간다 (enum 은 기본적으로 name() 으로 바뀐다).
 */
@Getter
@RequiredArgsConstructor
public enum TransportType {
    TRANSIT(5_000, 40), // 대중교통 (기본값)
    WALK(2_000, 25),    // 도보: 2km 넘게 찾지 않는다 (도보 5km ≈ 편도 1시간 30분)
    CAR(5_000, 30);     // 차량

    /** 장소 후보를 찾을 최대 반경(m). 반경 1·2·3·5km 단계 중 이 값 이하까지만 넓힌다 */
    private final int maxSearchRadiusMeters;

    /** 코스 안 한 구간의 최대 이동시간(분, 직선거리 어림 기준). 이보다 먼 장소는 코스에 넣지 않는다 */
    private final int maxLegMinutes;
}
