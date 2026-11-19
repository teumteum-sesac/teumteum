import type { CategoryCode } from '../../shared/types/domain'

/**
 * 카카오 카테고리 그룹 코드 → 우리 대분류 (스티커·아이콘 표시용). 매칭이 없으면 핀 아이콘을 쓴다.
 * 홈 검색창(HOM-01)과 주변 탐색 검색 결과(HOM-02)가 함께 쓴다
 */
export const KAKAO_GROUP_CATEGORY: Record<string, CategoryCode> = {
  CE7: 'CAFE', // 카페
  FD6: 'FOOD', // 음식점
  CT1: 'CULTURE', // 문화시설
  AT4: 'TOURISM', // 관광명소
}
