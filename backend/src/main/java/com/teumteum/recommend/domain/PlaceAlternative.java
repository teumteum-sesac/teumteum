package com.teumteum.recommend.domain;

/**
 * 장소 교체 후보 1곳과 "이 후보로 바꾸면 어떻게 되는지" 계산 결과 (DET-03 목록의 한 줄).
 * <p>
 * 이동시간은 모두 직선거리 추정이다 (이동시간 API 호출 안 함). 교체를 확정할 때 실제 API 로 다시 계산한다.
 *
 * @param place                후보 장소
 * @param stayMinutes          후보의 예상 체류시간(분). 세분류별 체류시간표
 * @param travelMinutes        직전 지점(직전 장소 또는 출발지) → 후보 이동시간(분), 추정
 * @param diffMinutes          바꿨을 때 총 소요시간 변화(분). 음수면 줄어듦
 * @param exceedsAvailableTime 바꾸면 이용 시간을 넘는지 (true 면 프론트가 회색으로 표시)
 */
public record PlaceAlternative(
        PlaceCandidate place,
        int stayMinutes,
        int travelMinutes,
        int diffMinutes,
        boolean exceedsAvailableTime
) {
}
