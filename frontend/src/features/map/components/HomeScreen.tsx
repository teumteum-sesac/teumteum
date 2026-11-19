import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { PAPER_STYLE } from '../../../shared/assets/stickerAssets'
import Button from '../../../shared/components/Button'
import Icon from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import BottomNav from '../../../shared/layout/BottomNav'
import { CATEGORIES, DEFAULT_POSITION, type CategoryCode, type LatLng } from '../../../shared/types/domain'
import { useConditionStore } from '../../condition/store'
import { requestCurrentPosition, useCurrentPosition } from '../hooks/useCurrentPosition'
import { useNearbyPlaces } from '../hooks/useNearbyPlaces'
import { MAP_BOTTOM_CLASS, type SheetLevel } from '../sheetLayout'
import NoticeBanner from '../../admin/components/NoticeBanner'
import HomeSearchBar from './HomeSearchBar'
import KakaoMap, { CLUSTER_MIN_LEVEL, type ClusterMarker, type KakaoMapController, type MapMarker, type MapPadding } from './KakaoMap'
import LocationGuide from './LocationGuide'
import MapControls, { LocateButton, MAP_CONTROLS_HEIGHT, type LocateStatus } from './MapControls'
import NearbySheet, { type NearbySearchView } from './NearbySheet'
import type { NearbyPlaceResult } from '../types'
import type { PlaceSearchResult } from '../../condition/types'
import { formatDistance, formatDistanceForRadius, straightDistance } from '../format'
import { useNearbySearch, type NearbySearchItem } from '../hooks/useNearbySearch'

/** 주변 탐색 진입 시 이 시간(ms) 안에 받은 현재 위치는 다시 쓴다 */
const REUSE_POSITION_MS = 2 * 60 * 1000

// ── 주변 탐색 (HOM-02) 반경 맞춤 ──
/** 반경 원에 맞출 때 지도 가장자리 여백(px). 위쪽은 검색창 묶음 아래끝 + 이 값 */
const RADIUS_FIT_GAP = 12
/**
 * 반경 원에 맞출 때 이보다 멀리 축소하지 않는다. 3km 원은 일부가 화면 밖으로 나가도 된다
 * (카카오 레벨: 숫자가 클수록 넓게. 375px 화면에서 500m → 5, 1km → 6, 3km → 7 에서 멈춤)
 */
const RADIUS_FIT_MAX_LEVEL = 7

// ── 홈 지도 컨트롤 (HOM-01 내 위치 · 확대/축소) ──
/** 홈 지도 첫 확대 수준 = 가장 멀리 볼 수 있는 수준 (카카오는 숫자가 작을수록 확대) */
const HOME_LEVEL = 4
/** 첫 화면에서 더 확대할 수 있는 단계 수 */
const ZOOM_STEPS_MAX = 3
/** 컨트롤 묶음 바닥과 하단 패널 윗변 사이 간격(px) */
const CONTROLS_GAP = 12
/** 컨트롤 위끝과 검색창 묶음 사이 최소 간격(px). 이만큼도 남지 않으면(실제로 겹칠 때) 컨트롤을 숨긴다 */
const CONTROLS_SPACE_MARGIN = 8
/** 검색창 묶음(위쪽 떠 있는 영역)의 아래 padding(px). p-4 와 같은 값 */
const TOP_OVERLAY_PADDING = 16
/** 위치 안내 카드와 검색창 묶음 사이 간격(px) */
const GUIDE_GAP = 12

/** 홈 지도 영역 안에서 잰 위치(px, 지도 영역 위쪽 기준) */
interface HomeLayout {
  /** 검색창 묶음(검색창 + 공지 + 위치 안내 문구) 아래끝 */
  searchBottom: number
  /** 하단 패널 윗변 */
  sheetTop: number
  /** 지도 영역 높이 */
  height: number
}

interface HomeScreenProps {
  /** true면 주변 장소 탐색 시트(HOM-02)를 연다 */
  nearby?: boolean
}

/**
 * 홈 / 바로 추천 (HOM-01) + 주변 장소 탐색 시트 (HOM-02). 지도·검색창·하단바는 고정.
 * 지도는 기본 좌표(서울시청)로 먼저 그리고, 현재 위치를 받으면 그 위치로 이동해 현재 위치 점을 표시한다.
 * 홈은 현재 위치 점만, 주변 탐색은 장소 마커와 현재 위치가 모두 보이게 범위를 맞춘다.
 */
export default function HomeScreen({ nearby = false }: HomeScreenProps) {
  const navigate = useNavigate()
  // 홈은 권한 변경·화면 복귀 시 내 위치 버튼 상태를 다시 판단한다
  // 주변 탐색은 홈에서 2분 안에 받은 위치가 있으면 위치를 다시 묻지 않고 바로 주변 장소를 부른다 (첫 로딩 단축)
  const { position, denied, loading, fixId, locate } = useCurrentPosition({
    watchPermission: !nearby,
    reuseRecentMs: nearby ? REUSE_POSITION_MS : undefined,
  })
  const setOrigin = useConditionStore((s) => s.setOrigin)
  const [radius, setRadius] = useState(1000)
  // 주변 탐색(HOM-02) 대분류 필터. null = 전체. 지도 마커와 시트 목록이 같은 값을 쓴다
  const [category, setCategory] = useState<CategoryCode | null>(null)
  // 세분류 필터 (추천 결과 RES-01 과 같은 두 단계). null = 고른 대분류 전체. 대분류를 고른 뒤에만 쓴다
  const [subCategory, setSubCategory] = useState<string | null>(null)
  // 주변 탐색에서 지도 마커로 고른 장소. 시트에서 해당 카드를 강조한다
  const [selectedPlaceId, setSelectedPlaceId] = useState<string | null>(null)
  // 마커를 누를 때마다 1씩 늘린다. 같은 마커를 다시 눌러도 시트가 해당 카드로 다시 스크롤하게 한다
  const [scrollRequest, setScrollRequest] = useState(0)
  // 주변 탐색 시트 높이. 지도 영역도 같은 만큼 줄인다 (HOM-02 ①)
  const [sheetLevel, setSheetLevel] = useState<SheetLevel>('half')

  // ── 주변 탐색 화면 안 검색 (HOM-02) ──
  // 검색 기준 좌표는 탐색 기준(position) 그대로. 검색해도 반경·카테고리·주변 장소 목록은 바뀌지 않고, 검색어를 지우면 그대로 돌아간다
  const { state: searchState, search: runSearch, clear: resetSearch } = useNearbySearch(position)
  const searching = nearby && searchState.status !== 'idle'

  // 주변 탐색(HOM-02) 장소: 실제 API(GET /api/places/nearby). 현재 위치를 받은 뒤(거부되면 서울시청 기준) 부르고,
  // 반경이 바뀌면 다시 부른다. 검색 중에는 목록이 보이지 않으므로 부르지 않고, 검색어를 지우면 캐시가 있으면 바로 쓰고 없으면 그때 부른다.
  // 홈(HOM-01)에서는 부르지 않는다
  const nearbyPlaces = useNearbyPlaces(position, radius, nearby && !loading && !searching)
  // 서버에서는 대분류 8개 전체를 받고, 카테고리 칩은 화면에서 거른다 (칩을 바꿀 때 다시 요청하지 않음)
  const categoryPlaces = useMemo(
    () => nearbyPlaces.places.filter((p) => category === null || p.category === category),
    [nearbyPlaces.places, category],
  )
  // 고른 대분류의 세분류 칩: 지금 반경 안에 실제로 있는 세분류만, 공통 목록(CATEGORIES) 순서로 보여 준다
  const subCategories = useMemo(() => {
    if (category === null) return []
    const present = new Set(categoryPlaces.map((p) => p.subCategory))
    const order = CATEGORIES.find((c) => c.code === category)?.subCategories ?? []
    return [...order.filter((name) => present.has(name)), ...[...present].filter((name) => !order.includes(name))]
  }, [category, categoryPlaces])
  // 반경을 바꿔 고른 세분류가 사라지면 대분류 전체로 본다
  const activeSubCategory = subCategory !== null && subCategories.includes(subCategory) ? subCategory : null
  const places = useMemo(
    () => (activeSubCategory === null ? categoryPlaces : categoryPlaces.filter((p) => p.subCategory === activeSubCategory)),
    [categoryPlaces, activeSubCategory],
  )
  // 검색 결과에서 고른 장소 id (주변 장소 선택과 따로 둔다)
  const [searchSelectedId, setSearchSelectedId] = useState<string | null>(null)
  // 검색 결과 → 화면용: 탐색 기준에서의 직선거리, 지금 반경 안인지. 반경을 바꾸면 다시 검색하지 않고 판정만 다시 한다
  const searchItems = useMemo<NearbySearchItem[]>(() => {
    if (searchState.status !== 'done') return []
    return searchState.results.map((r) => {
      const distance = straightDistance(position, { lat: r.latitude, lng: r.longitude })
      return {
        id: r.kakaoPlaceId ?? `addr-${r.latitude},${r.longitude}`,
        kakaoPlaceId: r.kakaoPlaceId,
        type: r.type,
        name: r.name,
        lat: r.latitude,
        lng: r.longitude,
        address: r.roadAddress ?? r.address,
        categoryGroupCode: r.categoryGroupCode,
        categoryLabel: r.categoryName?.split(' > ').pop() ?? null,
        distance,
        inRadius: distance <= radius,
      }
    })
  }, [searchState, position, radius])
  const selectedSearchItem = useMemo(
    () => (searching && searchSelectedId ? (searchItems.find((i) => i.id === searchSelectedId) ?? null) : null),
    [searching, searchItems, searchSelectedId],
  )

  // ── 주변 탐색 지도 표시 ──
  // 장소 마커는 클러스터로 묶는다 (기존 핑크 핀 이미지 그대로). 고른 장소는 클러스터에서 빼서 크게 강조한 핀으로 따로 그려
  // 묶여 있어도 항상 보이게 한다. 마커를 누르면 목록의 해당 카드를 강조한다 (시트가 접혀 있으면 반만 펼침)
  // 검색 중에는 주변 장소 대신 검색 결과를 같은 방식으로 그리고, 반경 밖 결과는 흐리게 한다 (빼지 않음)
  const clusterMarkers = useMemo<ClusterMarker[] | undefined>(() => {
    if (!nearby) return undefined
    if (searching) {
      return searchItems
        .filter((i) => i.id !== searchSelectedId)
        .map((i) => ({
          id: i.id,
          lat: i.lat,
          lng: i.lng,
          title: i.name,
          subtitle: [i.categoryLabel, `직선 ${formatDistanceForRadius(i.distance)}`].filter(Boolean).join(' · '),
          dimmed: !i.inRadius,
          onClick: () => {
            setSearchSelectedId(i.id)
            setScrollRequest((n) => n + 1)
            setSheetLevel((current) => (current === 'collapsed' ? 'half' : current))
          },
        }))
    }
    return places
      .filter((p) => p.kakaoPlaceId !== selectedPlaceId)
      .map((p) => ({
        id: p.kakaoPlaceId,
        lat: p.latitude,
        lng: p.longitude,
        title: p.name,
        subtitle: [p.subCategory, p.distance !== null ? formatDistance(p.distance) : null].filter(Boolean).join(' · '),
        onClick: () => {
          setSelectedPlaceId(p.kakaoPlaceId)
          setScrollRequest((n) => n + 1)
          setSheetLevel((current) => (current === 'collapsed' ? 'half' : current))
        },
      }))
  }, [nearby, searching, searchItems, searchSelectedId, places, selectedPlaceId])
  const selectedPlace = useMemo(
    () => (nearby && selectedPlaceId ? (places.find((p) => p.kakaoPlaceId === selectedPlaceId) ?? null) : null),
    [nearby, places, selectedPlaceId],
  )
  // 지금 강조할 장소: 검색 중이면 검색 결과에서, 아니면 주변 장소에서 고른 것
  const focused = useMemo(() => {
    if (searching) return selectedSearchItem ? { id: selectedSearchItem.id, lat: selectedSearchItem.lat, lng: selectedSearchItem.lng } : null
    return selectedPlace ? { id: selectedPlace.kakaoPlaceId, lat: selectedPlace.latitude, lng: selectedPlace.longitude } : null
  }, [searching, selectedSearchItem, selectedPlace])

  // 클러스터 밖에 따로 그리는 것: 고른 장소 강조 핀 + 현재 위치 스티커 (홈은 현재 위치만)
  const markers = useMemo<MapMarker[]>(() => {
    const list: MapMarker[] = []
    if (focused) list.push({ ...focused, variant: 'selected' })
    // 위치를 실제로 받았을 때만 현재 위치를 찍는다. 조회 중·거부·실패 시 position 은 서울시청 기본값이라 표시하지 않음
    if (!loading && !denied) list.push({ lat: position.lat, lng: position.lng, variant: 'current' })
    return list
  }, [focused, position, loading, denied])

  // 탐색 반경 원: 실제 주변 장소 조회 기준 좌표(position)가 중심. 지도를 끌어도 기준은 바뀌지 않는다
  const circle = useMemo(() => (nearby && !loading ? { center: position, radius } : null), [nearby, loading, position, radius])

  // 주변 탐색 지도를 축소하면 고른 장소 핀을 지운다 (핀이 묶음에서 빠져 혼자 덩그러니 남지 않게, 다시 묶음 안으로)
  const nearbyLevelRef = useRef<number | null>(null)
  const onNearbyLevelChange = useCallback((level: number) => {
    const prev = nearbyLevelRef.current
    nearbyLevelRef.current = level
    if (prev !== null && level > prev) {
      setSelectedPlaceId(null)
      setSearchSelectedId(null)
    }
  }, [])

  // 반경·대분류를 바꾸면 목록이 달라지므로 이전 선택은 해제한다
  const changeRadius = (next: number) => {
    setRadius(next)
    setSelectedPlaceId(null)
  }
  const changeCategory = (next: CategoryCode | null) => {
    setCategory(next)
    setSubCategory(null) // 대분류를 바꾸면 세분류 선택은 풀린다
    setSelectedPlaceId(null)
  }
  // 같은 세분류를 다시 누르면 선택을 푼다 (대분류 전체)
  const toggleSubCategory = (name: string) => {
    setSubCategory((current) => (current === name ? null : name))
    setSelectedPlaceId(null)
  }

  // ── 홈 지도 컨트롤 (HOM-01). 주변 탐색(HOM-02)에서는 쓰지 않는다 ──
  const areaRef = useRef<HTMLDivElement>(null)
  const topOverlayRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const [layout, setLayout] = useState<HomeLayout | null>(null)
  const [controller, setController] = useState<KakaoMapController | null>(null)
  const [level, setLevel] = useState(HOME_LEVEL)
  const [guideOpen, setGuideOpen] = useState(false)
  // 주변 탐색 지도 영역 (시트 위에서 끝남). 현재 위치 이동 시 보이는 영역 높이를 잰다
  const mapWrapRef = useRef<HTMLDivElement>(null)

  // 지금 화면에서 검색창 묶음 아래끝·패널 윗변을 잰다 (화면에 붙어 있지 않으면 null)
  const readLayout = useCallback((): HomeLayout | null => {
    const area = areaRef.current
    const top = topOverlayRef.current
    const panel = panelRef.current
    if (!area || !top || !panel) return null
    const a = area.getBoundingClientRect()
    return {
      searchBottom: top.getBoundingClientRect().bottom - a.top - TOP_OVERLAY_PADDING,
      sheetTop: panel.getBoundingClientRect().top - a.top,
      height: a.height,
    }
  }, [])

  // 공지·위치 안내 문구가 늦게 뜨거나 화면 크기가 바뀌면 다시 잰다
  useEffect(() => {
    const area = areaRef.current
    const top = topOverlayRef.current
    const panel = panelRef.current
    if (nearby || !area || !top || !panel) return
    const measure = () => setLayout(readLayout())
    measure()
    const observer = new ResizeObserver(measure)
    for (const el of [area, top, panel]) observer.observe(el)
    return () => observer.disconnect()
  }, [nearby, readLayout])

  // 위치를 새로 받을 때마다(첫 진입·내 위치 버튼·다시 시도·권한 허용) 확대 수준은 그대로 두고
  // 내 위치를 검색창과 패널 사이, 보이는 지도 영역의 가운데로 부드럽게 옮긴다.
  // 꺼짐 → 기본이 되면 같은 렌더에서 위치 안내 문구가 사라지므로, 저장된 값이 아니라 지금 화면을 바로 잰다
  const pannedFixRef = useRef(0)
  useEffect(() => {
    if (nearby || !controller || fixId === pannedFixRef.current) return
    const current = readLayout()
    if (!current) return
    pannedFixRef.current = fixId
    controller.panToVisible(position, (current.searchBottom + current.sheetTop) / 2)
  }, [nearby, controller, fixId, position, readLayout])

  // ── 현재 위치 버튼 (HOM-01·HOM-02 공통) ──
  // 주변 탐색은 지도만 옮기고 검색 기준 좌표(position)는 바꾸지 않는다 → 훅 상태를 건드리지 않는 requestCurrentPosition 사용.
  // 그 결과로 버튼 상태를 정한다 (null = 아직 안 누름 → 처음 위치 조회 결과를 따름)
  const [nearbyLocated, setNearbyLocated] = useState<boolean | null>(null)
  const nearbyRequestRef = useRef<Promise<boolean> | null>(null)
  const locateStatus: LocateStatus = (nearby ? (nearbyLocated === null ? denied : !nearbyLocated) : denied) ? 'off' : 'default'
  const [guideTop, setGuideTop] = useState(0)

  // 지금 화면에서 검색창 묶음(검색창 + 공지 + 위치 안내 문구) 아래끝 (지도 영역 위쪽 기준 px)
  const readSearchBottom = useCallback((): number | null => {
    const area = areaRef.current
    const top = topOverlayRef.current
    if (!area || !top) return null
    return top.getBoundingClientRect().bottom - area.getBoundingClientRect().top - TOP_OVERLAY_PADDING
  }, [])

  // 주변 탐색: 좌표를 검색창 아래 ~ 시트 위(보이는 지도 영역)의 가운데로 부드럽게 옮긴다. 확대 수준은 그대로
  const panNearbyTo = useCallback(
    (target: LatLng) => {
      const searchBottom = readSearchBottom()
      const map = mapWrapRef.current
      if (!controller || searchBottom === null || !map) return
      controller.panToVisible(target, (searchBottom + map.clientHeight) / 2)
    },
    [controller, readSearchBottom],
  )

  // 주변 탐색 카드 선택: 그 장소로 지도를 옮기고 마커를 강조한다 (확대 수준·반경·검색 기준 좌표는 그대로)
  const selectPlaceFromCard = useCallback(
    (place: NearbyPlaceResult) => {
      setSelectedPlaceId(place.kakaoPlaceId)
      panNearbyTo({ lat: place.latitude, lng: place.longitude })
    },
    [panNearbyTo],
  )

  // 검색 결과 선택: 반경 안팎과 관계없이 그 장소로 지도를 옮기고 강조한다 (반경 값·탐색 기준 좌표는 그대로)
  const selectSearchItem = useCallback(
    (item: NearbySearchItem) => {
      setSearchSelectedId(item.id)
      panNearbyTo({ lat: item.lat, lng: item.lng })
    },
    [panNearbyTo],
  )
  // 검색어 제출: 이전 검색 선택은 지우고, 결과가 보이게 시트를 반만 펼친다
  const submitSearch = useCallback(
    (query: string) => {
      setSearchSelectedId(null)
      setSheetLevel((current) => (current === 'collapsed' ? 'half' : current))
      void runSearch(query)
    },
    [runSearch],
  )
  // 검색창 추천 목록에서 하나 고르기: 그 검색어로 시트 검색 결과를 띄우고, 고른 장소를 바로 강조·지도 이동한다.
  // id 는 searchItems 와 같은 규칙(장소 ID, 주소면 좌표)으로 만들어, 시트 결과가 도착하면 같은 카드가 강조된다
  const pickSuggestion = useCallback(
    (query: string, result: PlaceSearchResult) => {
      submitSearch(query)
      setSearchSelectedId(result.kakaoPlaceId ?? `addr-${result.latitude},${result.longitude}`)
      setScrollRequest((n) => n + 1)
      panNearbyTo({ lat: result.latitude, lng: result.longitude })
    },
    [submitSearch, panNearbyTo],
  )
  // 검색어 지우기: 검색 전 주변 탐색으로 (반경·카테고리·주변 장소 선택은 그대로, 주변 장소는 다시 부르지 않음)
  const clearSearch = useCallback(() => {
    setSearchSelectedId(null)
    resetSearch()
  }, [resetSearch])
  const searchView = useMemo<NearbySearchView | null>(() => {
    // searching 이면 searchState 는 idle 이 아니다 (TypeScript 가 함께 좁혀 준다)
    if (!searching) return null
    return {
      query: searchState.query,
      status: searchState.status,
      errorMessage: searchState.status === 'error' ? searchState.message : null,
      items: searchItems,
      selectedId: searchSelectedId,
      onSelect: selectSearchItem,
      onRetry: () => submitSearch(searchState.query),
    }
  }, [searching, searchState, searchItems, searchSelectedId, selectSearchItem, submitSearch])

  // 주변 탐색: 반경을 정했을 때(처음 진입·반경 변경)만 반경 원이 보이는 영역에 들어오게 맞춘다.
  // 사용자가 지도를 끌거나 확대·축소한 뒤에는 다시 맞추지 않고, 시트 높이가 바뀌어도 맞추지 않는다
  const fittedRadiusRef = useRef<string | null>(null)
  useEffect(() => {
    if (!nearby || !controller || loading) return
    const key = `${position.lat},${position.lng},${radius}`
    if (fittedRadiusRef.current === key) return
    const searchBottom = readSearchBottom()
    if (searchBottom === null) return
    fittedRadiusRef.current = key
    const padding: MapPadding = { top: searchBottom + RADIUS_FIT_GAP, right: RADIUS_FIT_GAP, bottom: RADIUS_FIT_GAP, left: RADIUS_FIT_GAP }
    controller.fitCircle(position, radius, padding, RADIUS_FIT_MAX_LEVEL)
  }, [nearby, controller, loading, position, radius, readSearchBottom])

  // 위치 안내 카드: 검색창 묶음 바로 아래 + 12px
  const openGuide = () => {
    const searchBottom = readSearchBottom()
    if (searchBottom !== null) setGuideTop(searchBottom + GUIDE_GAP)
    setGuideOpen(true)
  }

  // 주변 탐색: 현재 위치를 받아 지도 중심만 옮긴다 (보이는 지도 영역의 가운데). 요청 중 중복 탭은 무시
  const locateNearby = useCallback((): Promise<boolean> => {
    if (nearbyRequestRef.current) return nearbyRequestRef.current
    const request = requestCurrentPosition()
      .then((found) => {
        setNearbyLocated(found !== null)
        if (found) panNearbyTo(found)
        return found !== null
      })
      .finally(() => {
        nearbyRequestRef.current = null
      })
    nearbyRequestRef.current = request
    return request
  }, [panNearbyTo])

  // 버튼: 기본이면 위치를 다시 받아 이동, 실패하거나 꺼짐이면 안내 카드 (지도는 움직이지 않음)
  const handleLocate = async () => {
    if (locateStatus === 'off') {
      openGuide()
      return
    }
    if (!(await (nearby ? locateNearby() : locate()))) openGuide()
  }
  const closeGuide = useCallback(() => setGuideOpen(false), [])
  const retryLocate = useCallback(async () => {
    if (await (nearby ? locateNearby() : locate())) setGuideOpen(false)
  }, [nearby, locateNearby, locate])

  // 남은 지도 영역이 부족하면 컨트롤을 숨긴다. 재기 전에도 숨겨 둔다
  const controlsHidden = !layout || layout.sheetTop - layout.searchBottom < MAP_CONTROLS_HEIGHT + CONTROLS_GAP + CONTROLS_SPACE_MARGIN

  // 지금 외출 추천: 출발지를 비워 두면 조건 화면(ConditionForm)이 현재 위치 + 그 자리 주소로 채운다.
  // 권한이 없으면 빈 채로 남아 직접 설정 안내
  const startRecommend = () => {
    setOrigin(null)
    navigate('/condition')
  }

  return (
    <div className="flex h-dvh flex-col">
      <div ref={areaRef} className="relative flex-1">
        {/* 주변 탐색이면 지도 아래쪽을 시트 높이만큼 비워 시트와 겹치지 않게 한다. 위·아래가 정해진 absolute 라 높이가 0이 되지 않는다 */}
        <div ref={mapWrapRef} className={`absolute inset-x-0 top-0 ${nearby ? MAP_BOTTOM_CLASS[sheetLevel] : 'bottom-0'}`}>
          {nearby ? (
            // 주변 탐색: 지도 이동·확대는 반경 맞춤(fitCircle)·카드 선택·현재 위치 버튼으로만 한다 (center·level 은 처음 값 고정).
            // 반경 선택과 지도 확대 수준은 서로 독립 (확대·축소해도 탐색 반경은 그대로)
            <KakaoMap
              center={DEFAULT_POSITION}
              markers={markers}
              level={HOME_LEVEL}
              clusterMarkers={clusterMarkers}
              circle={circle}
              // 묶음이 풀리는 레벨(CLUSTER_MIN_LEVEL)보다 더 확대하지 못하게 막는다 (그 아래는 지도가 깨져 보였다).
              // 이 레벨에서는 숫자 원을 누르면 개수와 상관없이 가게 목록이 뜬다
              minLevel={CLUSTER_MIN_LEVEL}
              onLevelChange={onNearbyLevelChange}
              onReady={setController}
              className="size-full"
            />
          ) : (
            // 홈: 지도 이동은 내 위치를 받을 때 위 effect 가 보이는 영역 가운데로 맞춘다 (center 는 처음 값 고정)
            // 확대 범위: 첫 화면(HOME_LEVEL)이 가장 멀리 본 상태, 3단계까지 확대. 핀치·휠도 같은 범위
            <KakaoMap
              center={DEFAULT_POSITION}
              markers={markers}
              level={HOME_LEVEL}
              minLevel={HOME_LEVEL - ZOOM_STEPS_MAX}
              maxLevel={HOME_LEVEL}
              onLevelChange={setLevel}
              onReady={setController}
              className="size-full"
            />
          )}
        </div>

        <div ref={topOverlayRef} className="absolute inset-x-0 top-0 z-30 flex flex-col gap-2 p-4">
          {nearby ? (
            // 주변 탐색: 결과는 이 화면 바텀시트에 보여 주고 다른 화면으로 가지 않는다
            <HomeSearchBar center={position} onSubmitQuery={submitSearch} onPickSuggestion={pickSuggestion} onClearQuery={clearSearch} />
          ) : (
            <HomeSearchBar center={loading || denied ? null : position} />
          )}
          {/* 전체 알림(REQ-053): 가장 최근 공지 1건. 주변 탐색 시트가 열려 있으면 지도를 가리지 않게 숨긴다 */}
          {!nearby && <NoticeBanner />}
          {denied && (
            <p className="paper-card px-3 py-2 text-xs text-neutral-600">
              위치를 사용할 수 없어 서울시청 기준으로 보여드려요. 위치를 직접 설정해 주세요.
            </p>
          )}
        </div>

        {!nearby && (
          <>
            {/* 키보드 순서: 검색창 → 확대 → 축소 → 내 위치 → 하단 패널 (DOM 순서를 그대로 따른다) */}
            <MapControls
              locateStatus={locateStatus}
              onLocate={() => void handleLocate()}
              onZoomIn={() => controller?.zoomBy(-1)}
              onZoomOut={() => controller?.zoomBy(1)}
              zoomInDisabled={level <= HOME_LEVEL - ZOOM_STEPS_MAX}
              zoomOutDisabled={level >= HOME_LEVEL}
              hidden={controlsHidden}
              style={
                layout
                  ? { bottom: layout.height - layout.sheetTop + CONTROLS_GAP, right: 'calc(16px + env(safe-area-inset-right))' }
                  : { right: 'calc(16px + env(safe-area-inset-right))' }
              }
            />
          </>
        )}

        {/* 주변 탐색(HOM-02): 현재 위치 버튼만 (확대·축소는 핀치·휠). 지도 영역(시트 위에서 끝남)의 오른쪽 아래 16px.
            시트를 끄는 동안에는 시트(z-20)가 위를 덮고, 전체로 펼치면 지도가 거의 없어 숨긴다 */}
        {nearby && (
          <div className={`pointer-events-none absolute inset-x-0 top-0 ${MAP_BOTTOM_CLASS[sheetLevel]}`}>
            <div
              inert={sheetLevel === 'full'}
              className={`pointer-events-auto absolute right-[calc(16px+env(safe-area-inset-right))] bottom-4 z-[5] transition-opacity duration-150 ${
                sheetLevel === 'full' ? 'pointer-events-none opacity-0' : 'opacity-100'
              }`}
            >
              <LocateButton status={locateStatus} onClick={() => void handleLocate()} />
            </div>
          </div>
        )}

        {guideOpen && locateStatus === 'off' && <LocationGuide top={guideTop} onClose={closeGuide} onRetry={retryLocate} />}

        {nearby ? (
          <NearbySheet
            radius={radius}
            onRadiusChange={changeRadius}
            category={category}
            onCategoryChange={changeCategory}
            subCategories={subCategories}
            subCategory={activeSubCategory}
            onSubCategoryToggle={toggleSubCategory}
            places={places}
            status={nearbyPlaces.status}
            errorMessage={nearbyPlaces.errorMessage}
            onRetry={nearbyPlaces.retry}
            selectedPlaceId={selectedPlaceId}
            onSelectPlace={selectPlaceFromCard}
            scrollRequest={scrollRequest}
            radiusEmpty={nearbyPlaces.places.length === 0}
            level={sheetLevel}
            onLevelChange={setSheetLevel}
            search={searchView}
          />
        ) : (
          // 홈 하단 패널 (HOM-01): 찢은 종이 위에 듀오 로고 + 노란 추천 버튼만 둔다.
          // 헤드라인·설명 문구와 바로가기 카드(주변 장소·저장한 코스)는 뺐다 — 바로가기는 하단바와 같은 메뉴라 겹친다
          // 그림자는 바깥 div, 물결 자르기는 안쪽 div (같은 요소에 주면 그림자가 잘린다)
          <div ref={panelRef} className="absolute inset-x-0 bottom-0 z-10 drop-shadow-[0_-4px_10px_rgba(32,32,32,0.12)]">
            <div className="torn-top bg-cream px-5 pt-5 pb-4" style={PAPER_STYLE}>
              {/* 로고 비율(약 20:9)을 ratio 로 맞춰 두면 이미지가 늦게 떠도 패널 높이가 바뀌지 않는다 (지도 컨트롤 위치가 패널 윗변 기준) */}
              <StickerImage name="brandLogoDuo" alt="틈틈" className="mx-auto w-64" ratio="aspect-[20/9]" />

              <Button className="mt-3 flex items-center justify-center gap-1" onClick={startRecommend}>
                외출 코스 추천받기
                <Icon name="chevronRight" className="size-5" strokeWidth={2.8} />
              </Button>
            </div>
          </div>
        )}
      </div>

      <BottomNav />
    </div>
  )
}
