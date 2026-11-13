import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import Button from '../../../shared/components/Button'
import BottomNav from '../../../shared/layout/BottomNav'
import { useConditionStore } from '../../condition/store'
import { useCurrentPosition } from '../hooks/useCurrentPosition'
import { MOCK_NEARBY } from '../mock'
import HomeSearchBar from './HomeSearchBar'
import KakaoMap, { type MapMarker } from './KakaoMap'
import NearbySheet from './NearbySheet'

interface HomeScreenProps {
  /** true면 주변 장소 탐색 시트(HOM-02)를 연다 */
  nearby?: boolean
}

/**
 * 홈 / 바로 추천 (HOM-01) + 주변 장소 탐색 시트 (HOM-02). 지도·검색창·하단바는 고정.
 * 지도는 기본 좌표(서울시청)로 먼저 그리고, 현재 위치를 받으면 현재 위치 점을 표시하고
 * 현재 위치와 장소 마커가 모두 보이게 범위를 맞춘다.
 */
export default function HomeScreen({ nearby = false }: HomeScreenProps) {
  const navigate = useNavigate()
  const { position, denied, loading } = useCurrentPosition()
  const setOrigin = useConditionStore((s) => s.setOrigin)
  const [radius, setRadius] = useState(1000)

  // 주변 장소 마커 예시 (API 연결 전 목업) + 현재 위치 점
  const markers = useMemo<MapMarker[]>(() => {
    const places: MapMarker[] = MOCK_NEARBY.filter((p) => !nearby || p.distance <= radius).map((p) => ({
      lat: position.lat + p.offset.lat,
      lng: position.lng + p.offset.lng,
    }))
    // 위치를 실제로 받았을 때만 현재 위치 점을 찍는다. 조회 중·거부·실패 시 position 은 서울시청 기본값이라 표시하지 않음
    if (loading || denied) return places
    return [...places, { lat: position.lat, lng: position.lng, variant: 'current' }]
  }, [position, nearby, radius, loading, denied])

  // 지금 외출 추천: 출발지 기본값은 현재 위치. 권한이 없으면 비운 채 진입해 직접 설정 안내
  const startRecommend = () => {
    setOrigin(denied ? null : { name: '현재 위치', ...position })
    navigate('/condition')
  }

  return (
    <div className="flex h-dvh flex-col">
      <div className="relative flex-1">
        <div className="absolute inset-0">
          <KakaoMap
            center={position}
            markers={markers}
            level={nearby && radius > 1000 ? 6 : 4}
            fitBounds
            className="size-full"
          />
        </div>

        <div className="absolute inset-x-0 top-0 z-30 flex flex-col gap-2 p-4">
          <HomeSearchBar />
          {denied && (
            <p className="rounded-lg bg-white/95 px-3 py-2 text-xs text-neutral-600 shadow">
              위치를 사용할 수 없어 서울시청 기준으로 보여드려요. 위치를 직접 설정해 주세요.
            </p>
          )}
        </div>

        {nearby ? (
          <NearbySheet radius={radius} onRadiusChange={setRadius} />
        ) : (
          <div className="absolute inset-x-0 bottom-0 z-10 flex flex-col gap-2 rounded-t-2xl bg-white p-4 shadow-[0_-4px_16px_rgba(0,0,0,0.08)]">
            <p className="mb-1 text-center font-bold">남는 시간, 어디로 가볼까요?</p>
            <Button onClick={startRecommend}>지금 외출 추천</Button>
            <Button variant="secondary" onClick={() => navigate('/nearby')}>
              주변 장소 탐색
            </Button>
          </div>
        )}
      </div>

      <BottomNav />
    </div>
  )
}
