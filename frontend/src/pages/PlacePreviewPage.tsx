import { useLocation, useParams } from 'react-router'
import PlacePreview, { type PlacePreviewState } from '../features/map/components/PlacePreview'
import AppHeader from '../shared/components/AppHeader'

/** 장소 미리보기 /places/:placeId (홈 검색·주변 탐색에서 진입). 장소 정보는 목록 화면이 location.state 로 넘긴다 */
export default function PlacePreviewPage() {
  const { placeId = '' } = useParams()
  const location = useLocation()
  const place = (location.state as { place?: PlacePreviewState } | null)?.place ?? null

  return (
    <>
      <AppHeader title="장소" />
      <PlacePreview placeId={placeId} place={place} />
    </>
  )
}
