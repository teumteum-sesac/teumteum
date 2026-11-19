package com.teumteum.recommend.domain;

/*
 * [이 파일이 하는 일]
 * 코스 안 장소 1곳의 "시간 정보"만 담는 작은 데이터 상자.
 *   - travelMinutes : 직전 지점(첫 장소면 출발지)에서 이 장소까지 오는 이동시간(분)
 *   - stayMinutes   : 이 장소에서 머무는 체류시간(분)
 *
 * 예) 출발지 ─15분─▶ 장소1(체류 60분)  →  new PlaceVisit(15, 60)
 *
 * 계산 로직은 없다. 값을 담아서 CourseTimeCalculator 에 넘기는 용도.
 *
 * 참고: record 가 자동으로 만들어 주는 것
 *   - 생성자           new PlaceVisit(15, 60)
 *   - 값 꺼내기        visit.travelMinutes(), visit.stayMinutes()   ← get 없이 필드 이름 그대로
 *   - 값 변경 불가     (필드가 final)
 */
public record PlaceVisit(int travelMinutes, int stayMinutes) {
}
