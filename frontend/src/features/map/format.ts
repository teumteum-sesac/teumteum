// 지도·주변 장소 화면에서 쓰는 표시용 함수. 목업 데이터와 상관없이 실제 API 연동 후에도 그대로 쓴다.
// (시간·날짜 표시는 여러 화면에서 쓰므로 shared/utils/format.ts 에 있다)

/** 거리 → "320m", "1.8km" */
export function formatDistance(meters: number): string {
  return meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`
}

/**
 * 반경 안팎을 함께 보여 줄 때의 거리 표시. km 는 0.1km 단위로 올림해서, 1,004m 처럼 반경(1km)을 조금 넘은 곳이
 * '1.0km · 반경 밖'으로 보이는 모순을 막는다 (1,004m → 1.1km, 1,000m → 1.0km)
 */
export function formatDistanceForRadius(meters: number): string {
  return meters < 1000 ? `${meters}m` : `${(Math.ceil(meters / 100) / 10).toFixed(1)}km`
}

/** 두 좌표 사이 직선거리(m, 반올림). 지구를 구로 본 하버사인 공식. 도보 경로 거리가 아니다 */
export function straightDistance(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6_371_000
  const rad = (deg: number) => (deg * Math.PI) / 180
  const dLat = rad(b.lat - a.lat)
  const dLng = rad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return Math.round(2 * R * Math.asin(Math.sqrt(h)))
}

/** 도보 시간(분). 분당 67m 기준 */
export function walkMinutes(meters: number): number {
  return Math.max(1, Math.round(meters / 67))
}

/** 카카오 장소 페이지 주소 (장소 ID 로 바로 열린다). 장소 미리보기의 [카카오맵에서 보기] */
export function getKakaoPlaceUrl(kakaoPlaceId: string): string {
  return `https://place.map.kakao.com/${encodeURIComponent(kakaoPlaceId)}`
}
