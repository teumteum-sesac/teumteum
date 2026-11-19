import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import Button from '../../../shared/components/Button'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import KakaoPlaceSheet from '../../../shared/components/KakaoPlaceSheet'
import { getCategoryLabel, type CategoryCode, type LatLng } from '../../../shared/types/domain'
import { getWalkRoute } from '../api/walkRouteApi'
import { formatDistance, straightDistance } from '../format'
import { requestCurrentPosition } from '../hooks/useCurrentPosition'
import KakaoMap, { type MapMarker } from './KakaoMap'
import MapLocateButton from './MapLocateButton'

/*
 * [이 파일이 하는 일]
 * 장소 상세 (/places/:placeId). 홈 검색·주변 탐색·코스 일정에서 장소를 누르면 여기로 온다.
 * 목록에서 받은 기본 정보(이름·분류·주소)와 지도만 보여 준다. 지도: 주변 탐색에서 왔으면 내 위치 → 가게 도보 경로와 '도보 N분', 그 밖(홈 검색·코스)에서는 가게 마커만
 *   (GET /api/routes/walk, 추천과 같은 이동시간 계산. 위치를 못 받거나 5km 넘게 멀면 가게 핀만). 사진·소개·영업시간·후기는 아래 카카오맵 시트가 보여 준다
 * (TourAPI 장소 상세는 쓰지 않는다: 일반 가게는 데이터가 거의 없어 '소개 없음'만 나왔다).
 * 카카오 장소 ID가 없으면(저장 코스의 예전 데이터 등) 시트에 이름·주소만 보여 준다.
 *
 * 하단 시트는 항상 표시하며 손잡이로 최소화·펼치기 한다. 카카오 장소 페이지의 영업시간·리뷰를 그대로 보여 준다.
 */
export interface PlacePreviewState {
  previewId?: string
  kakaoPlaceId: string
  name: string
  /** 우리 대분류. 홈 검색 결과처럼 모를 수 있다 */
  category?: CategoryCode | null
  /** 세분류 또는 카카오 카테고리 이름 */
  subCategory?: string | null
  address?: string | null
  latitude: number
  longitude: number
  /** 현재 위치에서 거리(m) */
  distance?: number | null
  imageUrl?: string
  description?: string
  openingHours?: string
  /**
   * true 면 지도에 내 위치 → 장소 도보 경로·'도보 N분'을 그린다. 주변 탐색(HOM-02)의 [상세 보기]에서만 켠다 (PlaceCard).
   * 홈 검색·코스 일정·코스 지도에서 오면 없음 → 장소 마커만 찍힌 지도 (이전 방식)
   */
  walkRoute?: boolean
}

/** 이보다 멀면(m) 도보 경로를 그리지 않는다 (걸어가기엔 먼 곳) */
const WALK_ROUTE_MAX_M = 5_000
/** 경로를 그릴 때 지도 가장자리 여백 (위: 도보 배지, 오른쪽 아래: 현재 위치 버튼) */
const WALK_BOUNDS_PADDING = { top: 48, right: 40, bottom: 40, left: 32 }

export default function PlacePreview({ placeId, place }: { placeId: string; place: PlacePreviewState | null }) {
  const navigate = useNavigate()
  // 카카오맵 시트가 처음에 이 영역(주소) 바로 아래부터 올라오게 하는 기준
  const actionsRef = useRef<HTMLDivElement>(null)
  const validPlace = place && (place.previewId ?? place.kakaoPlaceId) === placeId ? place : null
  // 지도 중심: 장소 좌표. 렌더마다 새 객체를 만들면 지도가 계속 장소로 되돌아가므로 좌표가 바뀔 때만 만든다
  const placeLat = validPlace?.latitude ?? 0
  const placeLng = validPlace?.longitude ?? 0
  const placeCenter = useMemo(() => ({ lat: placeLat, lng: placeLng }), [placeLat, placeLng])
  // 현재 위치 버튼으로 받은 위치. 받으면 지도를 그리로 옮기고 현재 위치 스티커를 찍는다
  const [located, setLocated] = useState<LatLng | null>(null)
  // 내 위치 → 가게 도보 경로 (들어올 때 한 번). 위치를 못 받거나 너무 멀면 null (가게 핀만)
  const [walk, setWalk] = useState<{ from: LatLng; path: LatLng[]; minutes: number; estimated: boolean } | null>(null)
  // 도보 경로는 주변 탐색에서 들어왔을 때만
  const showWalkRoute = validPlace?.walkRoute === true
  useEffect(() => {
    if (!showWalkRoute) return
    let active = true
    void (async () => {
      const from = await requestCurrentPosition()
      if (!active || !from || straightDistance(from, placeCenter) > WALK_ROUTE_MAX_M) return
      try {
        const route = await getWalkRoute(from, placeCenter)
        if (!active) return
        setWalk({
          from,
          path: route.path.map((p) => ({ lat: p.latitude, lng: p.longitude })),
          minutes: route.travelMinutes,
          estimated: route.estimated,
        })
      } catch {
        // 경로를 못 받으면 가게 핀만 보여 준다 (안내 없이)
      }
    })()
    return () => {
      active = false
    }
  }, [showWalkRoute, placeCenter])
  // 지도 표시: 가게 핀 (+ 경로가 있으면 내 위치 스티커). 둘 다 있으면 둘이 함께 보이게 범위를 맞춘다
  const mapMarkers = useMemo<MapMarker[]>(() => {
    const list: MapMarker[] = [{ lat: placeCenter.lat, lng: placeCenter.lng, title: validPlace?.name }]
    if (walk) list.push({ lat: walk.from.lat, lng: walk.from.lng, variant: 'current' })
    return list
  }, [placeCenter, validPlace?.name, walk])

  // 새로고침·주소 직접 입력으로 들어오면 넘겨받은 정보가 없다 → 카카오맵 링크로 안내
  if (!place || !validPlace) {
    return (
      <EmptyState
        sticker="sceneEmpty"
        message="장소 정보를 다시 불러올 수 없어요"
        description="목록에서 장소를 다시 눌러 주세요. 카카오맵에서도 볼 수 있어요."
        action={
          <>
            <Button className="mx-auto w-auto px-8" onClick={() => navigate('/nearby')}>
              주변 장소 보기
            </Button>
            <KakaoPlaceSheet kakaoPlaceId={placeId} />
          </>
        }
      />
    )
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      <section className="paper-card flex items-center gap-3 p-4">
        <span className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-primary-light/60 p-1.5">
          {place.category ? (
            <StickerImage name={CATEGORY_STICKER[place.category]} className="size-full" />
          ) : (
            <Icon name="location" className="size-8 text-primary-dark" />
          )}
        </span>
        <div className="min-w-0">
          <h2 className="font-display text-xl leading-snug">{place.name}</h2>
          <p className="mt-0.5 text-xs text-neutral-500">
            {[place.category ? getCategoryLabel(place.category) : null, place.subCategory].filter(Boolean).join(' › ')}
          </p>
          {place.distance != null && (
            <span className="mt-1 inline-block rounded-full bg-sunny/60 px-2 py-0.5 text-[11px] font-bold">
              여기서 {formatDistance(place.distance)}
            </span>
          )}
        </div>
      </section>

      <div className="relative overflow-hidden rounded-2xl border-2 border-line">
        <KakaoMap
          center={located ?? placeCenter}
          level={3}
          markers={mapMarkers}
          polyline={walk?.path ?? false}
          fitBounds={walk !== null}
          boundsPadding={WALK_BOUNDS_PADDING}
          currentPosition={walk ? null : located}
          className="h-52 w-full"
        />
        {/* 내 위치에서 걸어서 몇 분인지 (경로 API 실패로 직선 추정이면 '예상') */}
        {walk && (
          <span className="absolute top-2.5 left-2.5 z-10 flex items-center gap-1 rounded-full border-2 border-ink bg-sunny px-2.5 py-0.5 text-xs font-bold shadow-[0_2px_0_var(--color-ink)]">
            <StickerImage name="transportWalk" className="size-4" />내 위치에서 도보 {walk.minutes}분{walk.estimated && ' (예상)'}
          </span>
        )}
        <MapLocateButton onLocate={(position) => setLocated({ ...position })} />
      </div>

      <div ref={actionsRef}>
        {place.address && (
          <p className="flex items-start gap-2 px-1 text-sm text-neutral-700">
            <Icon name="location" className="mt-0.5 size-4 text-primary-dark" />
            {place.address}
          </p>
        )}
      </div>

      <KakaoPlaceSheet
        kakaoPlaceId={place.kakaoPlaceId || undefined}
        placeName={place.name}
        initialTopRef={actionsRef}
      >
        <div className="space-y-3 p-4 text-sm">
          <p className="font-bold">{place.name}</p>
          <p>{place.address}</p>
        </div>
      </KakaoPlaceSheet>
    </div>
  )
}
