import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import Button from '../../../shared/components/Button'
import BottomNav from '../../../shared/layout/BottomNav'
import type { CategoryCode } from '../../../shared/types/domain'
import { useConditionStore } from '../../condition/store'
import { useCurrentPosition } from '../hooks/useCurrentPosition'
import { MOCK_NEARBY } from '../mock'
import { MAP_BOTTOM_CLASS, type SheetLevel } from '../sheetLayout'
import HomeSearchBar from './HomeSearchBar'
import KakaoMap, { type BoundsPadding, type MapMarker } from './KakaoMap'
import NearbySheet from './NearbySheet'

/** 홈(HOM-01) 하단 패널('지금 외출 추천' 등) 높이 + 여유. 범위 맞춤 시 이 아래로 마커가 숨지 않게 한다 */
const HOME_PANEL_PADDING = 200

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
  // 주변 탐색(HOM-02) 대분류 필터. null = 전체. 지도 마커와 시트 목록이 같은 값을 쓴다
  const [category, setCategory] = useState<CategoryCode | null>(null)
  // 주변 탐색에서 지도 마커로 고른 장소. 시트에서 해당 카드를 강조한다
  const [selectedPlaceId, setSelectedPlaceId] = useState<string | null>(null)
  // 주변 탐색 시트 높이. 지도 영역도 같은 만큼 줄인다 (HOM-02 ①)
  const [sheetLevel, setSheetLevel] = useState<SheetLevel>('half')

  // 화면에 보여줄 장소 (API 연결 전 목업). 주변 탐색이면 반경 → 대분류 순서로 거른다
  // TODO(정종일): 주변 장소 API 연동 시 MOCK_NEARBY 대신 API 결과(placeId 포함)를 넣는다
  const radiusPlaces = useMemo(() => MOCK_NEARBY.filter((p) => !nearby || p.distance <= radius), [nearby, radius])
  const places = useMemo(
    () => radiusPlaces.filter((p) => !nearby || category === null || p.category === category),
    [radiusPlaces, nearby, category],
  )

  // 장소 마커 + 현재 위치 점
  const markers = useMemo<MapMarker[]>(() => {
    const placeMarkers: MapMarker[] = places.map((p) => ({
      id: p.placeId,
      lat: position.lat + p.offset.lat,
      lng: position.lng + p.offset.lng,
      // 주변 탐색에서만 마커를 누르면 목록의 해당 카드를 강조한다 (HOM-02 ①).
      // 시트가 접혀 있으면 카드가 보이도록 반만 펼친다
      onClick: nearby
        ? () => {
            setSelectedPlaceId(p.placeId)
            setSheetLevel((current) => (current === 'collapsed' ? 'half' : current))
          }
        : undefined,
    }))
    // 위치를 실제로 받았을 때만 현재 위치 점을 찍는다. 조회 중·거부·실패 시 position 은 서울시청 기본값이라 표시하지 않음
    if (loading || denied) return placeMarkers
    return [...placeMarkers, { lat: position.lat, lng: position.lng, variant: 'current' }]
  }, [position, places, nearby, loading, denied])

  // 범위 맞춤 여백: 지도 위에 떠 있는 검색창(약 80px, 위치 안내 문구가 있으면 375px 기준 약 136px)과
  // 홈 하단 패널에 마커가 가리지 않게 한다. 카카오 기본 마커는 아래 끝이 좌표라 위쪽에 마커 높이(약 42px)를 더한다.
  // 주변 탐색은 지도 영역이 시트 위에서 끝나므로 아래 여백만 작게 둔다
  const boundsPadding = useMemo<BoundsPadding>(
    () => ({ top: denied ? 188 : 132, right: 24, bottom: nearby ? 24 : HOME_PANEL_PADDING, left: 24 }),
    [denied, nearby],
  )

  // 반경·대분류를 바꾸면 목록이 달라지므로 이전 선택은 해제한다
  const changeRadius = (next: number) => {
    setRadius(next)
    setSelectedPlaceId(null)
  }
  const changeCategory = (next: CategoryCode | null) => {
    setCategory(next)
    setSelectedPlaceId(null)
  }

  // 지금 외출 추천: 출발지 기본값은 현재 위치. 권한이 없으면 비운 채 진입해 직접 설정 안내
  const startRecommend = () => {
    setOrigin(denied ? null : { name: '현재 위치', ...position })
    navigate('/condition')
  }

  return (
    <div className="flex h-dvh flex-col">
      <div className="relative flex-1">
        {/* 주변 탐색이면 지도 아래쪽을 시트 높이만큼 비워 시트와 겹치지 않게 한다. 위·아래가 정해진 absolute 라 높이가 0이 되지 않는다 */}
        <div className={`absolute inset-x-0 top-0 ${nearby ? MAP_BOTTOM_CLASS[sheetLevel] : 'bottom-0'}`}>
          <KakaoMap
            center={position}
            markers={markers}
            level={nearby && radius > 1000 ? 6 : 4}
            fitBounds
            boundsPadding={boundsPadding}
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
          <NearbySheet
            radius={radius}
            onRadiusChange={changeRadius}
            category={category}
            onCategoryChange={changeCategory}
            places={places}
            selectedPlaceId={selectedPlaceId}
            radiusEmpty={radiusPlaces.length === 0}
            level={sheetLevel}
            onLevelChange={setSheetLevel}
          />
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
