package com.teumteum.recommend.service;

import org.springframework.stereotype.Component;

@Component
public class PlaceCountCalculator {

    // 코스에 넣을 수 있는 최대 장소 수
    static final int MAX_PLACE_COUNT = 4;

    /**
     * 이용 시간(분)으로 코스에 넣을 최대 장소 수를 계산한다.
     * 60분 이하 → 1곳, 180분 이하 → 2곳, 300분 이하 → 3곳, 그 이상 → 4곳
     * 0분 이하는 요청 단계에서 막으므로 여기로 들어오지 않는다.
     */
    public int calculateMaxPlaceCount(int availableMinutes) {

        //1시간(60분) 이하면 1곳
        if (availableMinutes <= 60) return 1;

        //3시간(180분) 이하면 2곳
        if (availableMinutes <= 180) return 2;

        //5시간(300분) 이하면 3곳
        if (availableMinutes <= 300) return 3;

        //위에 하나도 안 걸리면(300분 초과) 최대 장소 수
        return MAX_PLACE_COUNT;
    }
}