// 지도·주변 장소 화면에서 쓰는 표시용 함수. 목업 데이터와 상관없이 실제 API 연동 후에도 그대로 쓴다.
// (시간·날짜 표시는 여러 화면에서 쓰므로 shared/utils/format.ts 에 있다)

/** 거리 → "320m", "1.8km" */
export function formatDistance(meters: number): string {
  return meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`
}

/** 도보 시간(분). 분당 67m 기준 */
export function walkMinutes(meters: number): number {
  return Math.max(1, Math.round(meters / 67))
}
