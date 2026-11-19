package com.teumteum.recommend.service;

import com.teumteum.recommend.domain.CoursePlan;
import com.teumteum.recommend.domain.CourseStop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 확정한 코스의 남는 시간을 체류시간으로 채운다 (#204).
 * <pre>
 * 예) 이용 시간 2시간, 목표 100%(120분)
 *     출발 ─5분─▶ 카페(40) ─5분─▶ 공원(40)                = 90분, 30분 남음
 *     5분씩 번갈아 늘리기 → 카페 55, 공원 55              = 120분
 * </pre>
 * - 장소마다 최대 체류시간(StayTimeCalculator.calculateMax)까지만 늘린다. 다 차면 남은 시간은 그대로 둔다
 * - 5분 단위로 늘린다. "57분" 같은 값보다 "55분" 이 화면에서 읽기 쉽고, 실제 예상값도 그 정도 정확도다
 * - 장소를 돌아가며 5분씩 넣어서 한 곳만 길어지지 않게 한다
 * - 이동시간은 바꾸지 않으므로 이동시간 API 를 다시 부르지 않는다 (NFR-014)
 */
@Component
@RequiredArgsConstructor
public class StayTimeFiller {

    /** 한 번에 늘리는 체류시간(분) */
    static final int STEP_MINUTES = 5;

    private final StayTimeCalculator stayTimeCalculator;
    private final CourseTimeCalculator courseTimeCalculator;

    /**
     * @param course        이동시간까지 확정한 코스 (체류시간은 최소값)
     * @param targetMinutes 채울 목표 총 소요시간(분). 이용 시간 이하로 넘겨야 한다
     * @return 체류시간을 늘린 새 코스. 남는 시간이 5분 미만이면 원래 코스 그대로
     */
    public CoursePlan fill(CoursePlan course, int targetMinutes) {
        int remaining = targetMinutes - course.totalMinutes();
        if (remaining < STEP_MINUTES) {
            return course;
        }

        List<CourseStop> stops = course.stops();
        int[] stays = new int[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            stays[i] = stops.get(i).stayMinutes();
        }

        // 한 바퀴 돌 때마다 장소마다 5분씩. 한 바퀴 동안 아무 데도 못 늘렸으면(모두 최대) 멈춘다
        boolean added = true;
        while (remaining >= STEP_MINUTES && added) {
            added = false;
            for (int i = 0; i < stops.size() && remaining >= STEP_MINUTES; i++) {
                int max = stayTimeCalculator.calculateMax(stops.get(i).place().subCategory());
                if (stays[i] + STEP_MINUTES <= max) {
                    stays[i] += STEP_MINUTES;
                    remaining -= STEP_MINUTES;
                    added = true;
                }
            }
        }

        List<CourseStop> filled = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            CourseStop stop = stops.get(i);
            filled.add(new CourseStop(stop.place(), stays[i], stop.travel()));
        }
        // 총 시간·이동 합·추정 여부를 처음 만들 때와 같은 방법으로 다시 계산
        return courseTimeCalculator.toCoursePlan(filled, course.finalTravel());
    }
}
