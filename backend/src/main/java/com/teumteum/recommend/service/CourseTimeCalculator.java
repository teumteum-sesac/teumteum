package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import com.teumteum.recommend.domain.PlaceVisit;
import com.teumteum.recommend.domain.TravelRoute;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/*
 * 코스 "전체"의 시간을 다루는 계산기.
 *   1) calculateTotalMinutes   : 총 소요시간(분) 계산 (REQ-023)
 *   2) isWithinAvailableTime   : 총 소요시간이 이용 시간 안인지 확인 (REQ-024)
 *   3) toCoursePlan            : 방문지 목록으로 총 시간·이동 합·추정 여부까지 채운 CoursePlan 만들기
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

    /**
     * 방문지 목록 → 총 소요시간·이동 합·추정 여부를 계산해 CoursePlan 으로 만든다.
     * <p>
     * 코스를 처음 만들 때(CourseComposer)와 장소를 교체할 때(PlaceReplaceService) 똑같이 쓴다.
     * 원래 CourseComposer 안에 있던 것을 여기로 옮겼다 → 두 곳의 계산 방식이 어긋나지 않는다.
     *
     * @param stops       방문 순서대로 방문지 (각 travel 은 직전 지점 → 그 장소)
     * @param finalTravel 마지막 장소 → 도착지 이동. 도착지가 없으면 null
     */
    public CoursePlan toCoursePlan(List<CourseStop> stops, TravelRoute finalTravel) {
        List<PlaceVisit> visits = new ArrayList<>();
        int travelSum = 0;
        boolean estimated = false;

        for (CourseStop stop : stops) {
            visits.add(new PlaceVisit(stop.travel().travelMinutes(), stop.stayMinutes()));
            travelSum += stop.travel().travelMinutes();
            estimated = estimated || stop.travel().estimated(); // 하나라도 추정이면 true
        }

        int finalTravelMinutes = 0;
        if (finalTravel != null) {
            finalTravelMinutes = finalTravel.travelMinutes();
            estimated = estimated || finalTravel.estimated();
        }

        int totalMinutes = calculateTotalMinutes(visits, finalTravelMinutes);
        return new CoursePlan(stops, finalTravel, totalMinutes, travelSum + finalTravelMinutes, estimated);
    }
}
