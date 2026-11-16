import { useEffect, useRef, useState } from 'react'
import type { LatLng } from '../../../shared/types/domain'
import { loadKakaoMap } from '../api/loadKakaoMap'
import type { KakaoMapInstance, KakaoMapsApi, KakaoOverlay } from '../types'

/** 지도 마커. label이 있으면 S·순번·E 같은 라벨 마커로, 없으면 기본 마커로 그린다 */
export interface MapMarker extends LatLng {
  label?: string
  /** 라벨 마커 색 (출발·도착은 어둡게, 장소는 기본색) */
  tone?: 'primary' | 'dark'
  /** 누르면 뜨는 말풍선 문구 */
  title?: string
  /** 'current'면 현재 위치를 파란 원형 점으로 그린다 (label·tone·title 무시) */
  variant?: 'current'
  /** 마커를 구분하는 값 (예: 장소 placeId). 렌더링에는 쓰지 않는다 */
  id?: string
  /** 마커를 눌렀을 때 실행 (기본 마커·라벨 마커) */
  onClick?: () => void
}

interface KakaoMapProps {
  center: LatLng
  /** 확대 수준 (작을수록 확대) */
  level?: number
  markers?: MapMarker[]
  /** true면 마커를 순서대로 잇는 경로선을 그린다 (대중교통은 1차 버전에서 직선) */
  polyline?: boolean
  /** true면 처음 그릴 때 마커가 모두 보이게 맞춘다 */
  fitBounds?: boolean
  /** 범위 맞춤 여백. 없으면 카카오 기본값 */
  boundsPadding?: BoundsPadding
  className?: string
}

// 기본값을 바깥에 둬야 매 렌더마다 새 배열이 생겨 지도 effect가 다시 도는 것을 막는다
const NO_MARKERS: MapMarker[] = []

const TONE_COLOR = {
  primary: '#3b6cf6',
  dark: '#404040',
}

function createLabelElement(marker: MapMarker): HTMLElement {
  // 바깥 상자는 핀 크기 그대로 둔다. CustomOverlay 는 처음 크기로 기준점을 잡으므로
  // 말풍선이 상자 크기를 바꾸면 핀이 좌표에서 밀린다 → 말풍선은 absolute 로 핀 위에 띄운다
  const wrap = document.createElement('div')
  wrap.style.cssText = 'position:relative;cursor:pointer'

  const bubble = document.createElement('div')
  bubble.textContent = marker.title ?? ''
  bubble.style.cssText =
    'display:none;position:absolute;bottom:100%;left:50%;transform:translateX(-50%);margin-bottom:4px;padding:4px 8px;border-radius:8px;background:#fff;font-size:12px;box-shadow:0 1px 4px rgba(0,0,0,.2);white-space:nowrap'

  const pin = document.createElement('div')
  pin.textContent = marker.label ?? ''
  pin.style.cssText = `display:flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:9999px;border:2px solid #fff;background:${TONE_COLOR[marker.tone ?? 'primary']};color:#fff;font-size:12px;font-weight:700;box-shadow:0 1px 4px rgba(0,0,0,.3)`

  if (marker.title) {
    pin.addEventListener('click', () => {
      bubble.style.display = bubble.style.display === 'none' ? 'block' : 'none'
    })
  }
  if (marker.onClick) pin.addEventListener('click', marker.onClick)
  wrap.append(bubble, pin)
  return wrap
}

/** 현재 위치 표시용 파란 원형 점 */
function createCurrentLocationElement(): HTMLElement {
  const dot = document.createElement('div')
  dot.setAttribute('aria-label', '현재 위치')
  dot.style.cssText = `width:16px;height:16px;border-radius:9999px;border:3px solid #fff;background:${TONE_COLOR.primary};box-shadow:0 0 0 4px rgba(59,108,246,.25),0 1px 4px rgba(0,0,0,.3)`
  return dot
}

function drawOverlays(maps: KakaoMapsApi, map: KakaoMapInstance, markers: MapMarker[], polyline: boolean): KakaoOverlay[] {
  const overlays: KakaoOverlay[] = markers.map((m) => {
    const position = new maps.LatLng(m.lat, m.lng)
    // 현재 위치 점은 좌표가 점의 정중앙에 오도록 yAnchor 0.5
    if (m.variant === 'current') {
      return new maps.CustomOverlay({ position, content: createCurrentLocationElement(), map, yAnchor: 0.5 })
    }
    if (m.label) {
      return new maps.CustomOverlay({ position, content: createLabelElement(m), map, yAnchor: 1 })
    }
    const marker = new maps.Marker({ position, map })
    if (m.onClick) maps.event.addListener(marker, 'click', m.onClick)
    return marker
  })
  if (polyline && markers.length > 1) {
    overlays.push(
      new maps.Polyline({
        path: markers.map((m) => new maps.LatLng(m.lat, m.lng)),
        strokeWeight: 4,
        strokeColor: TONE_COLOR.primary,
        strokeOpacity: 0.8,
        map,
      }),
    )
  }
  return overlays
}

/** 범위 맞춤 시 지도 가장자리에서 비울 여백(px). 지도 위에 떠 있는 검색창 등에 마커가 가리지 않게 한다 */
export interface BoundsPadding {
  top: number
  right: number
  bottom: number
  left: number
}

/** 여백을 빼고 남는 지도 공간이 이보다 작으면 여백 없이 맞춘다 (시트를 전체로 올려 지도가 아주 작을 때) */
const MIN_FIT_SPACE = 80

/** 마커가 모두 보이도록 지도 범위를 맞춘다 */
function fitToMarkers(
  maps: KakaoMapsApi,
  map: KakaoMapInstance,
  markers: MapMarker[],
  padding: BoundsPadding | undefined,
  container: HTMLElement | null,
) {
  const bounds = new maps.LatLngBounds()
  markers.forEach((m) => bounds.extend(new maps.LatLng(m.lat, m.lng)))
  const usable =
    padding &&
    container &&
    container.clientHeight - padding.top - padding.bottom >= MIN_FIT_SPACE &&
    container.clientWidth - padding.left - padding.right >= MIN_FIT_SPACE
  if (padding && usable) map.setBounds(bounds, padding.top, padding.right, padding.bottom, padding.left)
  else map.setBounds(bounds)
}

/** 카카오 지도. SDK 로드는 loadKakaoMap 한 곳에서만 한다 (AGENTS.md 6장) */
export default function KakaoMap({
  center,
  level = 4,
  markers = NO_MARKERS,
  polyline = false,
  fitBounds = false,
  boundsPadding,
  className = '',
}: KakaoMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapsRef = useRef<KakaoMapsApi | null>(null)
  const mapRef = useRef<KakaoMapInstance | null>(null)
  const overlayRefs = useRef<KakaoOverlay[]>([])
  const [ready, setReady] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 지도는 처음 위치·확대 수준으로 한 번만 만든다. 이후 변경은 아래 effect들이 반영
  const initialRef = useRef({ center, level })
  // 이번 렌더에서 fitBounds로 화면을 맞췄는지. 같은 렌더의 panTo·setLevel이 맞춘 화면을 덮어쓰지 않게 한다
  const boundsFittedRef = useRef(false)
  // 크기 변경 감지(ResizeObserver) 콜백에서 최신 마커·fitBounds·여백 값을 읽기 위한 보관소
  const latestRef = useRef({ markers, fitBounds, boundsPadding })

  // 지도 생성: 한 번만
  useEffect(() => {
    let cancelled = false
    loadKakaoMap()
      .then((maps) => {
        if (cancelled || !containerRef.current || mapRef.current) return
        const { center: c, level: l } = initialRef.current
        mapsRef.current = maps
        mapRef.current = new maps.Map(containerRef.current, { center: new maps.LatLng(c.lat, c.lng), level: l })
        setReady(true)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        // 상세 원인(키 누락·로드 실패 등)은 개발자용으로 콘솔에만 남기고, 화면에는 사용자용 안내만 보여준다
        console.error('[KakaoMap] 지도 로드 실패:', e)
        setError('지도를 불러오지 못했어요. 잠시 후 새로고침해 주세요.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 마커·경로선
  useEffect(() => {
    latestRef.current = { markers, fitBounds, boundsPadding }
    const maps = mapsRef.current
    const map = mapRef.current
    if (!ready || !maps || !map) return
    overlayRefs.current.forEach((o) => o.setMap(null))
    overlayRefs.current = drawOverlays(maps, map, markers, polyline)

    if (fitBounds && markers.length > 1) {
      fitToMarkers(maps, map, markers, boundsPadding, containerRef.current)
      boundsFittedRef.current = true
    }
  }, [ready, markers, polyline, fitBounds, boundsPadding])

  // 중심 이동: 마지막으로 받은 center 와 같은 객체면 건너뛴다 (처음 그릴 때 fitBounds로 맞춘 화면을 덮지 않도록)
  // 좌표가 아니라 객체로 비교하므로, 같은 좌표라도 새 객체를 넘기면 다시 이동한다 (DET-02 같은 항목 재선택)
  useEffect(() => {
    const maps = mapsRef.current
    const { center: initial } = initialRef.current
    if (!ready || !maps || !mapRef.current) return
    if (initial === center) return
    initialRef.current = { ...initialRef.current, center }
    // 같은 렌더에서 범위를 맞췄으면 그 화면을 유지한다 (마커에 이 위치가 이미 포함됨)
    if (boundsFittedRef.current) return
    mapRef.current.panTo(new maps.LatLng(center.lat, center.lng))
  }, [ready, center])

  // 확대 수준: 처음 값과 같으면 건너뛴다
  useEffect(() => {
    if (!ready || !mapRef.current || initialRef.current.level === level) return
    initialRef.current = { ...initialRef.current, level }
    // 같은 렌더에서 범위를 맞췄으면 맞춘 확대 수준을 유지한다
    if (boundsFittedRef.current) return
    mapRef.current.setLevel(level)
  }, [ready, level])

  // 컨테이너 크기 변경 (HOM-02 시트 높이 변경, 화면 회전 등)
  // 카카오 지도는 컨테이너 크기가 바뀐 것을 스스로 알지 못하므로 relayout() 을 불러야 하고,
  // 범위 맞춤을 쓰는 지도는 줄어든/늘어난 영역 기준으로 다시 맞춘다. 아니면 기존 중심을 유지한다
  useEffect(() => {
    const container = containerRef.current
    if (!ready || !container) return
    let frame = 0
    const observer = new ResizeObserver(() => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => {
        const maps = mapsRef.current
        const map = mapRef.current
        if (!maps || !map) return
        const prevCenter = map.getCenter()
        map.relayout()
        const { markers: latestMarkers, fitBounds: fit, boundsPadding: padding } = latestRef.current
        if (fit && latestMarkers.length > 1) fitToMarkers(maps, map, latestMarkers, padding, container)
        else map.setCenter(prevCenter)
      })
    })
    observer.observe(container)
    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
    }
  }, [ready])

  // 매 렌더 마지막에 실행: 범위 맞춤 표시는 그 렌더에서만 유효하게 초기화한다
  // (effect는 선언 순서대로 실행되므로 반드시 위 effect들 다음에 둔다)
  useEffect(() => {
    boundsFittedRef.current = false
  })

  return (
    <div className={`relative bg-neutral-200 ${className}`}>
      <div ref={containerRef} className="absolute inset-0" />
      {error && (
        <div className="absolute inset-0 flex items-center justify-center p-6 text-center text-sm text-neutral-500">
          {error}
        </div>
      )}
    </div>
  )
}
