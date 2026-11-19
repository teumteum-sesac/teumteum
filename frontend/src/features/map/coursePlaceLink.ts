import type { CoursePlace } from '../recommend/types'
import type { PlacePreviewState } from './components/PlacePreview'

/**
 * 코스 안 장소 → 장소 상세 화면(/places/:placeId) 으로 갈 주소와 넘길 정보.
 * 코스 일정(CourseSchedule)과 코스 지도 [상세보기](CourseMap)가 같은 화면으로 가도록 함께 쓴다.
 * 저장 코스처럼 카카오 장소 ID가 없으면 'saved-{placeId}' 로 구분한다 (내부 ID를 카카오 ID로 쓰지 않음)
 *
 *   const { to, state } = coursePlaceLink(place)
 *   navigate(to, { state })
 */
export function coursePlaceLink(place: CoursePlace): { to: string; state: { place: PlacePreviewState } } {
  const previewId = place.kakaoPlaceId ?? `saved-${place.placeId}`
  return {
    to: `/places/${encodeURIComponent(previewId)}`,
    state: {
      place: {
        previewId,
        kakaoPlaceId: place.kakaoPlaceId ?? '',
        name: place.name,
        category: place.category,
        subCategory: place.subCategory,
        address: place.address,
        latitude: place.lat,
        longitude: place.lng,
        imageUrl: place.imageUrl,
        description: place.description,
        openingHours: place.openingHours,
      },
    },
  }
}
