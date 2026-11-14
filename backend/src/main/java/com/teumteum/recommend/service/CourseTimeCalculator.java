package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.PlaceVisit;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * 코스 "전체"의 시간을 다루는 계산기.
 *   1) calculateTotalMinutes   : 총 소요시간(분) 계산 (REQ-023)
 *   2) isWithinAvailableTime   : 총 소요시간이 이용 시간 안인지 확인 (REQ-024)
 *
 * 예) 출발지 ─15분─▶ 장소1(60분) ─10분─▶ 장소2(90분) ─20분─▶ 도착지
 *     visits = [PlaceVisit(15, 60), PlaceVisit(10, 90)], finalTravelMinutes = 20
 *     총 소요시간 = 15 + 60 + 10 + 90 + 20 = 195분
 */
@Component
public class CourseTimeCalculator {

    /**
     * 코스의 총 소요시간(분)을 계산한다.
     * 장소별 (직전 지점 → 장소 이동 + 체류) 합계에 마지막 장소 → 도착지 이동을 더한다.
     * 도착지가 없으면 finalTravelMinutes 는 0 이다.
     */
    public int calculateTotalMinutes(List<PlaceVisit> visits, int finalTravelMinutes) {
        int totalMinutes = 0;

        for (PlaceVisit visit : visits) {
            totalMinutes += visit.travelMinutes() + visit.stayMinutes();
        }

        return totalMinutes + finalTravelMinutes;
    }

    /**
     * 총 소요시간이 이용 시간 안에 들어오는지 확인한다.
     * 이용 시간과 딱 같으면 들어온 것으로 본다.
     */
    public boolean isWithinAvailableTime(int totalMinutes, int availableMinutes) {
        return totalMinutes <= availableMinutes;
    }
}
