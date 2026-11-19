import { useEffect, useRef, useState } from 'react'
import { getStickerUrl } from '../../../shared/assets/stickerAssets'
import type { LatLng } from '../../../shared/types/domain'
import { loadKakaoMap } from '../api/loadKakaoMap'
import type {
  KakaoCircle,
  KakaoCluster,
  KakaoCustomOverlay,
  KakaoLatLngBounds,
  KakaoMapInstance,
  KakaoMapsApi,
  KakaoMarker,
  KakaoMarkerClusterer,
  KakaoMarkerImage,
  KakaoOverlay,
} from '../types'

/**
 * 지도 마커. label이 있으면 S·순번 같은 라벨 마커(핀 스티커 + 글자)로, 없으면 장소 핀 마커로 그린다.
 * 스티커 이미지 파일이 없으면 예전 모양(색 원 라벨·카카오 기본 마커·파란 점)으로 그린다
 */
export interface MapMarker extends LatLng {
  label?: string
  /** 스티커 파일이 없을 때 라벨 마커 색 (출발·도착은 어둡게, 장소는 기본색) */
  tone?: 'primary' | 'dark'
  /** 'flag'면 라벨 마커를 깃발 스티커로 그린다 (코스 도착지 E, 글자는 그리지 않음) */
  icon?: 'flag'
  /** 누르면 뜨는 말풍선 문구 */
  title?: string
  /**
   * 'current'면 현재 위치 스티커로 그린다 (label·tone·title 무시).
   * 'selected'면 고른 장소를 크게 강조한 핀으로 그린다 (주변 탐색·검색에서 고른 장소. 다른 마커·클러스터보다 위)
   */
  variant?: 'current' | 'selected'
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
  /**
   * 경로선. true면 마커를 순서대로 직선으로 잇고, 좌표 배열을 주면 그 좌표를 따라 그린다 (REQ-041 실제 경로, routePath.ts).
   * 배열은 렌더마다 새로 만들지 말고 useMemo 로 넘긴다 (바뀌면 마커·경로선을 다시 그림)
   */
  polyline?: boolean | LatLng[]
  /** true면 처음 그릴 때 마커가 모두 보이게 맞춘다 */
  fitBounds?: boolean
  /** 범위 맞춤 여백. 없으면 카카오 기본값 */
  boundsPadding?: BoundsPadding
  /**
   * 사용자가 지도를 끌거나 확대·축소해 움직임이 끝날 때마다 지도 중심 좌표를 알려 준다 (CON-02 지도 중앙 핀).
   * 받은 좌표를 다시 center 로 넘기지 않는다 (지도가 이미 그 위치에 있음)
   */
  onCenterChange?: (center: LatLng) => void
  /**
   * 바깥(예: DET-02 방문 순서 목록)에서 라벨 마커를 선택한다. 마커 id 로 찾아 강조하고 말풍선을 연다.
   * 같은 마커를 다시 골라도 동작하도록 매번 새 객체로 넘긴다
   */
  selectMarker?: { id: string } | null
  /** 가장 많이 확대할 수 있는 레벨 (작을수록 확대). 버튼·핀치·휠 모두 이 범위 안에서만 움직인다 */
  minLevel?: number
  /** 가장 멀리 볼 수 있는 레벨 */
  maxLevel?: number
  /** 확대 수준이 바뀔 때마다 (버튼·핀치·휠 모두). 처음 그릴 때도 한 번 알린다 */
  onLevelChange?: (level: number) => void
  /** 지도가 만들어지면 확대·이동 손잡이를 넘긴다 (홈 지도 컨트롤) */
  onReady?: (controller: KakaoMapController) => void
  /**
   * 클러스터로 묶어 그릴 장소 마커 (주변 탐색 HOM-02). 기존 핑크 핀 스티커 이미지를 그대로 쓰고,
   * 가까운 마커는 확대 수준에 따라 숫자 원으로 묶였다 풀린다. id 가 같은 마커는 다시 만들지 않고 재사용한다.
   * 배열은 useMemo 로 넘긴다
   */
  clusterMarkers?: ClusterMarker[]
  /** 탐색 반경 원 (중심·반지름 m). null 이면 그리지 않는다 */
  circle?: { center: LatLng; radius: number } | null
  className?: string
}

/** 클러스터로 묶이는 장소 마커 */
export interface ClusterMarker extends LatLng {
  id: string
  /** true면 흐리게 (검색 결과 중 반경 밖) */
  dimmed?: boolean
  onClick?: () => void
}

/** 지도 가장자리에서 비울 여백(px) */
export interface MapPadding {
  top: number
  right: number
  bottom: number
  left: number
}

/** 바깥(홈 지도 컨트롤)에서 지도를 움직이는 손잡이 */
export interface KakaoMapController {
  /** 지도 중심을 기준으로 한 단계 확대(-1)·축소(+1). minLevel~maxLevel 을 넘지 않는다 */
  zoomBy(delta: -1 | 1): void
  /**
   * 확대 수준은 그대로 두고, 좌표가 지도 위 targetY(px, 지도 위쪽 기준) 높이에 오도록 부드럽게 옮긴다.
   * 시트·검색창에 가리지 않는 영역의 가운데에 맞출 때 쓴다
   */
  panToVisible(target: LatLng, targetY: number): void
  /**
   * 반경 원(center·radius m)이 padding 을 뺀 영역에 들어오도록 맞춘다.
   * 다만 maxLevel 보다 멀리 축소하지는 않는다 (넓은 반경은 원 일부가 화면 밖으로 나가도 된다)
   */
  fitCircle(center: LatLng, radius: number, padding: MapPadding, maxLevel: number): void
}

// ── 주변 탐색(HOM-02) 클러스터 · 반경 원 ──
/** 이 레벨 이상(멀리 볼 때)에서만 마커를 묶는다. 더 확대하면 모두 개별 마커 */
const CLUSTER_MIN_LEVEL = 3
/** 묶는 격자 크기(px) */
const CLUSTER_GRID = 60
/** 검색 결과 중 반경 밖 마커의 투명도 */
const DIMMED_OPACITY = 0.45
/** 클러스터 원: 메인 핑크 + 흰 숫자 + 흰 외곽선 + 약한 그림자. 묶인 개수(10 미만 / 30 미만 / 그 이상)에 따라 38·44·50px */
function clusterStyle(size: number): Partial<CSSStyleDeclaration> {
  return {
    width: `${size}px`,
    height: `${size}px`,
    lineHeight: `${size - 4}px`,
    boxSizing: 'border-box',
    borderRadius: '9999px',
    border: '2px solid #fff',
    background: '#F26FA8',
    color: '#fff',
    fontSize: '14px',
    fontWeight: '800',
    textAlign: 'center',
    boxShadow: '0 2px 6px rgba(0,0,0,.25)',
  }
}
const CLUSTER_STYLES = [clusterStyle(38), clusterStyle(44), clusterStyle(50)]

/** 현재 위치와 겹친 클러스터 배지를 놓을 원의 반지름(px). 현재 위치 핑크 후광(76px) 바로 바깥이라 캐릭터를 가리지 않는다 */
const NEAR_ME_BADGE_RADIUS = 42

/**
 * 현재 위치 스티커와 겹친 클러스터 표시 (HOM-02).
 * 클러스터 좌표·크기·클릭은 그대로 두고 모양만 바꾼다: 숫자 원을 투명하게 해 캐릭터가 보이게 하고,
 * 묶인 개수는 스티커 오른쪽 위 작은 배지로만 보여 준다 (배지를 누르면 클러스터처럼 확대). 겹치지 않으면 원래 숫자 원으로 되돌린다
 */
function styleClusterNearMe(el: HTMLElement, near: boolean) {
  const badge = el.querySelector<HTMLElement>('[data-near-me-count]')
  if (!near) {
    if (el.dataset.nearMe !== '1') return
    delete el.dataset.nearMe
    badge?.remove()
    Object.assign(el.style, { background: '#F26FA8', color: '#fff', borderColor: '#fff', boxShadow: '0 2px 6px rgba(0,0,0,.25)' })
    return
  }
  // 카카오가 넣은 숫자(글자 노드)만 읽는다 (배지 글자는 제외)
  const count = [...el.childNodes]
    .filter((node) => node.nodeType === Node.TEXT_NODE)
    .map((node) => node.textContent ?? '')
    .join('')
    .trim()
  const size = el.offsetWidth
  el.dataset.nearMe = '1'
  Object.assign(el.style, {
    background: 'transparent',
    color: 'transparent',
    borderColor: 'transparent',
    boxShadow: 'none',
  })
  if (getComputedStyle(el).position === 'static') el.style.position = 'relative'
  const next = badge ?? document.createElement('span')
  next.setAttribute('data-near-me-count', '')
  next.textContent = count
  // 스티커 오른쪽 위(45°) 지점. 현재 위치 라벨(위 가운데)과 겹치지 않는다
  const offset = NEAR_ME_BADGE_RADIUS * Math.SQRT1_2
  next.style.cssText = `position:absolute;left:${size / 2 + offset}px;top:${size / 2 - offset}px;transform:translate(-50%,-50%);min-width:28px;height:28px;padding:0 7px;box-sizing:border-box;border-radius:9999px;border:2px solid #fff;background:#F26FA8;color:#fff;font-size:13px;font-weight:800;line-height:24px;text-align:center;white-space:nowrap;box-shadow:0 2px 6px rgba(0,0,0,.25)`
  if (!badge) el.append(next)
}
/** 반경 원: 메인 핑크 얇은 선 + 아주 연한 안쪽 */
const CIRCLE_STYLE = { strokeWeight: 1.5, strokeColor: '#F26FA8', strokeOpacity: 0.9, fillColor: '#F26FA8', fillOpacity: 0.07 }
/** 위도 1도의 거리(m) */
const METERS_PER_DEGREE = 111_320

// 기본값을 바깥에 둬야 매 렌더마다 새 배열이 생겨 지도 effect가 다시 도는 것을 막는다
const NO_MARKERS: MapMarker[] = []

const TONE_COLOR = {
  primary: '#3b6cf6',
  dark: '#404040',
}

/** 경로선 색을 못 읽었을 때 쓰는 값 (index.css 의 --color-primary 와 같은 메인 핑크) */
const ROUTE_COLOR_FALLBACK = '#f26fa8'

/**
 * 경로선 색. 카카오 Polyline 은 CSS 클래스를 못 쓰므로 디자인 토큰 --color-primary(index.css @theme)를 실제 값으로 읽어 넘긴다.
 * 토큰이 바뀌면 경로선 색도 같이 바뀐다
 */
function getRouteColor(): string {
  return getComputedStyle(document.documentElement).getPropertyValue('--color-primary').trim() || ROUTE_COLOR_FALLBACK
}

// ── 지도 스티커 (docs/design-sticker.md 지도 핀: map-pin · map-flag · map-me) ──
// 기준점 = 이미지 안에서 마커 좌표에 놓일 위치. 512×512 원본에서 측정한 픽셀을 비율로 둔다.
// 이미지가 늦게 떠도 좌표가 밀리지 않도록 표시 크기를 모두 고정한다 (CustomOverlay·MarkerImage 모두 처음 크기로 기준점을 잡음)
const STICKER_PX = 512
/** map-pin 핀 끝 (254, 452) */
const PIN_TIP = { x: 254 / STICKER_PX, y: 452 / STICKER_PX }
/** map-pin 가운데 흰 원 중심 (252, 215). 여기에 S·순번 글자를 얹는다 */
const PIN_CIRCLE = { x: 252 / STICKER_PX, y: 215 / STICKER_PX }
/** map-flag 깃대 아래 끝 (135, 449) */
const FLAG_BASE = { x: 135 / STICKER_PX, y: 449 / STICKER_PX }
/** 표시 크기(px) */
const LABEL_PIN_SIZE = 48 // 코스 지도 S·순번
const FLAG_SIZE = 44 // 코스 지도 도착 E
const PLACE_PIN_SIZE = 36 // 주변 탐색 장소 (개수가 많아 작게)
const SELECTED_PIN_SIZE = 52 // 주변 탐색·검색에서 고른 장소 (크게 강조)
const ME_SIZE = 54 // 현재 위치 스티커 (흰 원 60px 안)

/** 스티커 이미지 한 장을 고정 크기로 */
function createStickerImage(url: string, size: number): HTMLImageElement {
  const img = document.createElement('img')
  img.src = url
  img.alt = ''
  img.width = size
  img.height = size
  img.draggable = false
  img.style.cssText = `display:block;width:${size}px;height:${size}px`
  return img
}

/** 말풍선이 지도 가장자리에서 떨어질 최소 간격(px). 이보다 가까우면 지도를 그만큼만 옮긴다 */
const BUBBLE_EDGE_GAP = 12
/** 겹칠 때 순서: 기본 라벨 마커 < 선택한 마커 < 현재 위치 */
const Z_LABEL = 1
const Z_SELECTED = 10
const Z_CURRENT = 20
/** 주변 탐색 클러스터 숫자 원 (현재 위치 위) */
const Z_CLUSTER = 25
/** 주변 탐색·검색에서 고른 장소 핀 (가장 위) */
const Z_FOCUSED = 30
/** 현재 위치: 바깥 핑크 후광 · 흰 원 지름(px) */
const ME_HALO_SIZE = 76
const ME_RING_SIZE = 60
/** 현재 위치 pulse 는 페이지에서 처음 한 번만 (지도가 다시 그려질 때마다 반복하지 않음) */
let mePulsePlayed = false

/** 라벨 마커 하나를 선택·해제하는 손잡이 */
interface LabelControl {
  overlay: KakaoCustomOverlay
  /** 말풍선 (없으면 null) */
  bubble: HTMLElement | null
  setSelected: (selected: boolean) => void
}

/** CustomOverlay 에 넣을 내용과 기준점 */
interface LabelContent {
  content: HTMLElement
  /** 누르는 부분 (핀·깃발) */
  pin: HTMLElement
  bubble: HTMLElement | null
  xAnchor: number
  yAnchor: number
  setSelected: (selected: boolean) => void
}

/** 말풍선: 장소명(굵게) + 체류·다음 이동(보조). title 은 '장소명 · 체류 … · 다음 이동 …' 모양 */
function createBubble(title: string): HTMLElement {
  const bubble = document.createElement('div')
  // 핀 위 가운데, 핀과 10px 떨어뜨려 숫자를 가리지 않는다. 폭은 160~200px, 길면 줄바꿈
  bubble.style.cssText =
    'display:none;position:absolute;bottom:100%;left:50%;transform:translateX(-50%);margin-bottom:10px;box-sizing:border-box;width:max-content;min-width:160px;max-width:200px;padding:8px 12px;border-radius:12px;background:#fff;color:#202020;box-shadow:0 4px 12px rgba(0,0,0,.18);text-align:center;white-space:normal;word-break:keep-all;overflow-wrap:anywhere;cursor:default'
  const [name, ...rest] = title.split(' · ')
  const nameEl = document.createElement('p')
  nameEl.textContent = name
  nameEl.style.cssText = 'margin:0;font-size:14px;font-weight:700;line-height:1.35'
  bubble.append(nameEl)
  if (rest.length > 0) {
    const sub = document.createElement('p')
    sub.textContent = rest.join(' · ')
    sub.style.cssText = 'margin:2px 0 0;font-size:12px;line-height:1.4;color:#5f5f5f'
    bubble.append(sub)
  }
  // 아래 꼬리 (마커 쪽)
  const tail = document.createElement('div')
  tail.style.cssText =
    'position:absolute;left:50%;bottom:-5px;width:10px;height:10px;background:#fff;transform:translateX(-50%) rotate(45deg);box-shadow:2px 2px 3px rgba(0,0,0,.08)'
  bubble.append(tail)
  return bubble
}

/** S·순번 라벨 마커 (핀 스티커 + 흰 원 안 글자) 또는 도착 깃발 (icon: 'flag'). 누르면 선택·말풍선 */
function createLabelOverlay(marker: MapMarker): LabelContent {
  const isFlag = marker.icon === 'flag'
  const url = getStickerUrl(isFlag ? 'mapFlag' : 'mapPin')
  const size = isFlag ? FLAG_SIZE : LABEL_PIN_SIZE
  // 핀은 핀 끝, 깃발은 깃대 아래 끝이 좌표. 예전 원은 아래 가운데
  const anchor = !url ? { x: 0.5, y: 1 } : isFlag ? FLAG_BASE : PIN_TIP

  // 바깥 상자는 핀 크기 그대로 둔다. CustomOverlay 는 처음 크기로 기준점을 잡으므로
  // 말풍선이 상자 크기를 바꾸면 핀이 좌표에서 밀린다 → 말풍선은 absolute 로 핀 위에 띄운다
  const wrap = document.createElement('div')
  wrap.style.cssText = url ? `position:relative;width:${size}px;height:${size}px` : 'position:relative'

  const bubble = marker.title ? createBubble(marker.title) : null

  // 누르는 부분 (핀·깃발). 선택하면 좌표 기준점(핀 끝)을 중심으로 커져서 좌표는 그대로다
  const pin = document.createElement('div')
  const origin = `${anchor.x * 100}% ${anchor.y * 100}%`
  if (url) {
    pin.style.cssText = `position:absolute;inset:0;cursor:pointer;transform-origin:${origin};transition:transform .15s ease,filter .15s ease;filter:drop-shadow(0 1px 2px rgba(0,0,0,.2))`
    pin.append(createStickerImage(url, size))
    if (!isFlag && marker.label) {
      const text = document.createElement('span')
      text.textContent = marker.label
      text.style.cssText = `position:absolute;left:${PIN_CIRCLE.x * 100}%;top:${PIN_CIRCLE.y * 100}%;transform:translate(-50%,-50%);font-size:13px;font-weight:700;line-height:1;color:#202020`
      pin.append(text)
    }
  } else {
    // 스티커 파일이 없으면 예전 색 원 라벨
    pin.textContent = marker.label ?? ''
    pin.style.cssText = `display:flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:9999px;border:2px solid #fff;background:${TONE_COLOR[marker.tone ?? 'primary']};color:#fff;font-size:12px;font-weight:700;box-shadow:0 1px 4px rgba(0,0,0,.3);cursor:pointer;transform-origin:${origin};transition:transform .15s ease`
  }

  const setSelected = (selected: boolean) => {
    if (bubble) bubble.style.display = selected ? 'block' : 'none'
    pin.style.transform = selected ? 'scale(1.08)' : ''
    // 선택: 흰 링 + 연한 핑크 빛 + 그림자 / 기본: 옅은 그림자
    if (url) {
      pin.style.filter = selected
        ? 'drop-shadow(0 0 1.5px #fff) drop-shadow(0 0 1.5px #fff) drop-shadow(0 0 6px rgba(242,111,168,.85)) drop-shadow(0 3px 6px rgba(0,0,0,.25))'
        : 'drop-shadow(0 1px 2px rgba(0,0,0,.2))'
    }
  }

  // 지도 클릭(선택 해제)으로 번지지 않게 막는다
  for (const type of ['mousedown', 'touchstart', 'click']) wrap.addEventListener(type, (e) => e.stopPropagation())
  if (marker.onClick) pin.addEventListener('click', marker.onClick)
  if (bubble) wrap.append(bubble)
  wrap.append(pin)
  return { content: wrap, pin, bubble, xAnchor: anchor.x, yAnchor: anchor.y, setSelected }
}

/** 현재 위치 스티커 (map-me) + 흰 원 + 핑크 후광 + '내 위치' 라벨. 좌표가 원 가운데에 온다 */
function createCurrentLocationElement(): HTMLElement {
  const url = getStickerUrl('mapMe')
  const el = document.createElement('div')
  el.setAttribute('aria-label', '현재 위치')
  if (url) {
    el.setAttribute('role', 'img')
    // 바깥 상자는 후광 크기로 고정 (기준점이 밀리지 않게). 라벨은 상자 밖 위쪽에 absolute
    el.style.cssText = `position:relative;width:${ME_HALO_SIZE}px;height:${ME_HALO_SIZE}px;pointer-events:none`
    const halo = document.createElement('div')
    halo.style.cssText = 'position:absolute;inset:0;border-radius:9999px;background:rgba(242,111,168,.22);border:1.5px solid rgba(242,111,168,.4);box-sizing:border-box'
    const ring = document.createElement('div')
    ring.style.cssText = `position:absolute;left:50%;top:50%;width:${ME_RING_SIZE}px;height:${ME_RING_SIZE}px;transform:translate(-50%,-50%);border-radius:9999px;background:#fff;box-shadow:0 2px 8px rgba(0,0,0,.22);display:flex;align-items:center;justify-content:center`
    ring.append(createStickerImage(url, ME_SIZE))
    const label = document.createElement('div')
    label.textContent = '내 위치'
    label.setAttribute('aria-hidden', 'true')
    label.style.cssText = 'position:absolute;bottom:100%;left:50%;transform:translateX(-50%);margin-bottom:2px;padding:2px 8px;border-radius:9999px;border:1.5px solid #f26fa8;background:#fff;color:#202020;font-size:11px;font-weight:700;line-height:1.4;white-space:nowrap;box-shadow:0 1px 3px rgba(0,0,0,.15)'
    el.append(halo, ring, label)
    // 처음 한 번만 은은한 pulse (움직임 줄이기 설정이면 생략)
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (!mePulsePlayed && !reduceMotion && typeof halo.animate === 'function') {
      mePulsePlayed = true
      halo.animate(
        [
          { transform: 'scale(.7)', opacity: 0.9 },
          { transform: 'scale(1.15)', opacity: 0.4 },
          { transform: 'scale(1)', opacity: 1 },
        ],
        { duration: 1200, easing: 'ease-out' },
      )
    }
  } else {
    // 스티커 파일이 없으면 예전 파란 점
    el.style.cssText = `width:16px;height:16px;border-radius:9999px;border:3px solid #fff;background:${TONE_COLOR.primary};box-shadow:0 0 0 4px rgba(59,108,246,.25),0 1px 4px rgba(0,0,0,.3)`
  }
  return el
}

/** 고른 장소 핀 (주변 탐색·검색): 같은 핑크 핀 스티커를 크게 + 흰 테두리·핑크 빛. 스티커 파일이 없으면 null */
function createSelectedPinElement(): HTMLElement | null {
  const url = getStickerUrl('mapPin')
  if (!url) return null
  const el = document.createElement('div')
  el.setAttribute('role', 'img')
  el.setAttribute('aria-label', '선택한 장소')
  el.style.cssText = `width:${SELECTED_PIN_SIZE}px;height:${SELECTED_PIN_SIZE}px;pointer-events:none;filter:drop-shadow(0 0 1.5px #fff) drop-shadow(0 0 1.5px #fff) drop-shadow(0 0 7px rgba(242,111,168,.9)) drop-shadow(0 3px 6px rgba(0,0,0,.25))`
  el.append(createStickerImage(url, SELECTED_PIN_SIZE))
  return el
}

/** 장소 핀 마커 이미지 (주변 탐색). 모든 장소 마커가 이 이미지 하나를 함께 쓴다. 파일이 없으면 null → 카카오 기본 마커 */
function createPlacePinImage(maps: KakaoMapsApi): KakaoMarkerImage | null {
  const url = getStickerUrl('mapPin')
  if (!url) return null
  const offset = new maps.Point(Math.round(PIN_TIP.x * PLACE_PIN_SIZE), Math.round(PIN_TIP.y * PLACE_PIN_SIZE))
  return new maps.MarkerImage(url, new maps.Size(PLACE_PIN_SIZE, PLACE_PIN_SIZE), { offset })
}

/**
 * 마커·경로선을 그린다. 라벨 마커는 id(없으면 label)로 선택 손잡이를 labels 에 담고,
 * 누르면 onLabelClick(id) 를 부른다 (선택·해제는 KakaoMap 이 한 곳에서 관리)
 */
function drawOverlays(
  maps: KakaoMapsApi,
  map: KakaoMapInstance,
  markers: MapMarker[],
  polyline: boolean | LatLng[],
  onLabelClick: (id: string) => void,
): { overlays: KakaoOverlay[]; labels: Map<string, LabelControl> } {
  // 장소 핀 이미지는 장소 마커가 있을 때 한 번만 만든다
  let placePinImage: KakaoMarkerImage | null | undefined
  const labels = new Map<string, LabelControl>()
  const overlays: KakaoOverlay[] = markers.map((m, i) => {
    const position = new maps.LatLng(m.lat, m.lng)
    // 현재 위치는 좌표가 그림 정중앙에 오도록 yAnchor 0.5, 다른 마커보다 위에 둔다
    if (m.variant === 'current') {
      return new maps.CustomOverlay({ position, content: createCurrentLocationElement(), map, yAnchor: 0.5, zIndex: Z_CURRENT })
    }
    // 고른 장소: 클러스터·다른 마커·현재 위치보다 위에 크게 강조한 핀 (스티커 파일이 없으면 카카오 기본 마커)
    if (m.variant === 'selected') {
      const content = createSelectedPinElement()
      if (content) return new maps.CustomOverlay({ position, content, map, xAnchor: PIN_TIP.x, yAnchor: PIN_TIP.y, zIndex: Z_FOCUSED })
      return new maps.Marker({ position, map, zIndex: Z_FOCUSED })
    }
    if (m.label) {
      const { content, pin, bubble, xAnchor, yAnchor, setSelected } = createLabelOverlay(m)
      const overlay = new maps.CustomOverlay({ position, content, map, xAnchor, yAnchor, zIndex: Z_LABEL })
      const id = m.id ?? `${m.label}-${i}`
      labels.set(id, {
        overlay,
        bubble,
        setSelected: (selected) => {
          setSelected(selected)
          overlay.setZIndex(selected ? Z_SELECTED : Z_LABEL)
        },
      })
      pin.addEventListener('click', () => onLabelClick(id))
      return overlay
    }
    // 장소 마커는 카카오 Marker 그대로 (이미지만 핀 스티커). 많아도 가볍고 클릭 이벤트를 그대로 쓴다
    if (placePinImage === undefined) placePinImage = createPlacePinImage(maps)
    const marker = new maps.Marker(placePinImage ? { position, map, image: placePinImage } : { position, map })
    if (m.onClick) maps.event.addListener(marker, 'click', m.onClick)
    return marker
  })
  const path = Array.isArray(polyline) ? polyline : polyline ? markers : []
  if (path.length > 1) {
    overlays.push(
      new maps.Polyline({
        path: path.map((p) => new maps.LatLng(p.lat, p.lng)),
        strokeWeight: 4,
        strokeColor: getRouteColor(),
        strokeOpacity: 0.8,
        map,
      }),
    )
  }
  return { overlays, labels }
}

/**
 * 말풍선이 지도 밖으로 잘리면 잘린 만큼만 지도를 옮긴다 (가운데로 무조건 옮기지 않음).
 * 말풍선이 그려진 다음 프레임에 실제 위치를 재서 계산한다
 */
function panToShowBubble(map: KakaoMapInstance, container: HTMLElement, bubble: HTMLElement) {
  requestAnimationFrame(() => {
    const c = container.getBoundingClientRect()
    const b = bubble.getBoundingClientRect()
    let dx = 0
    let dy = 0
    if (b.left < c.left + BUBBLE_EDGE_GAP) dx = b.left - (c.left + BUBBLE_EDGE_GAP)
    else if (b.right > c.right - BUBBLE_EDGE_GAP) dx = b.right - (c.right - BUBBLE_EDGE_GAP)
    if (b.top < c.top + BUBBLE_EDGE_GAP) dy = b.top - (c.top + BUBBLE_EDGE_GAP)
    if (dx !== 0 || dy !== 0) map.panBy(Math.round(dx), Math.round(dy))
  })
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
  fitToBounds(map, bounds, padding, container)
}

/** 범위에 맞춘다. 여백을 빼고 남는 공간이 MIN_FIT_SPACE 보다 작으면 여백 없이 맞춘다 */
function fitToBounds(map: KakaoMapInstance, bounds: KakaoLatLngBounds, padding: BoundsPadding | undefined, container: HTMLElement | null) {
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
  onCenterChange,
  selectMarker,
  minLevel,
  maxLevel,
  onLevelChange,
  onReady,
  clusterMarkers,
  circle,
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
  // 라벨 마커 선택: 한 번에 하나만. 선택하면 강조 + 말풍선, 해제하면 원래대로
  const labelsRef = useRef(new Map<string, LabelControl>())
  const selectedIdRef = useRef<string | null>(null)
  const selectLabelRef = useRef((id: string | null, showWholeBubble: boolean) => {
    const prev = selectedIdRef.current
    if (prev && prev !== id) labelsRef.current.get(prev)?.setSelected(false)
    selectedIdRef.current = id
    if (!id) return
    const control = labelsRef.current.get(id)
    control?.setSelected(true)
    if (showWholeBubble && control?.bubble && mapRef.current && containerRef.current) {
      panToShowBubble(mapRef.current, containerRef.current, control.bubble)
    }
  })

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
    // 마커 클릭: 같은 마커를 다시 누르면 해제, 다른 마커면 이전 것을 해제하고 선택 (말풍선이 잘리면 지도를 조금 옮김)
    const { overlays, labels } = drawOverlays(maps, map, markers, polyline, (id) =>
      selectLabelRef.current(selectedIdRef.current === id ? null : id, true),
    )
    overlayRefs.current = overlays
    labelsRef.current = labels
    selectedIdRef.current = null

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

  // 지도 움직임이 끝날 때(idle) 중심 좌표 알림. onCenterChange 를 넘긴 지도만 이벤트를 등록한다
  // 콜백은 ref 로 최신 함수를 부르므로, 상위에서 함수를 새로 만들어도 이벤트를 다시 등록하지 않는다
  const onCenterChangeRef = useRef(onCenterChange)
  useEffect(() => {
    onCenterChangeRef.current = onCenterChange
  })
  const listensCenter = onCenterChange !== undefined
  useEffect(() => {
    const maps = mapsRef.current
    const map = mapRef.current
    if (!ready || !maps || !map || !listensCenter) return
    const handleIdle = () => {
      const c = map.getCenter()
      onCenterChangeRef.current?.({ lat: c.getLat(), lng: c.getLng() })
    }
    maps.event.addListener(map, 'idle', handleIdle)
    return () => maps.event.removeListener(map, 'idle', handleIdle)
  }, [ready, listensCenter])

  // 지도 빈 곳을 누르면 선택 해제 (말풍선 닫기). 마커를 누른 것은 마커 쪽에서 지도로 번지지 않게 막아 둠
  useEffect(() => {
    const maps = mapsRef.current
    const map = mapRef.current
    if (!ready || !maps || !map) return
    const handleMapClick = () => selectLabelRef.current(null, false)
    maps.event.addListener(map, 'click', handleMapClick)
    return () => maps.event.removeListener(map, 'click', handleMapClick)
  }, [ready])

  // 바깥(방문 순서 목록)에서 고른 마커 강조. 지도 이동은 center 로 하므로 여기서는 옮기지 않는다
  useEffect(() => {
    if (!ready || !selectMarker) return
    selectLabelRef.current(selectMarker.id, false)
  }, [ready, selectMarker])

  // ── 확대 범위 · 확대 수준 알림 · 바깥 손잡이 (홈 지도 컨트롤) ──
  // 콜백·범위는 ref 로 최신 값을 읽어, 상위에서 새로 만들어도 이벤트를 다시 등록하지 않는다
  const rangeRef = useRef({ minLevel, maxLevel })
  const onLevelChangeRef = useRef(onLevelChange)
  const onReadyRef = useRef(onReady)
  useEffect(() => {
    rangeRef.current = { minLevel, maxLevel }
    onLevelChangeRef.current = onLevelChange
    onReadyRef.current = onReady
  })

  // 확대 범위: SDK 의 범위 제한이 있으면 그걸로 막는다 (핀치·휠·더블탭 포함)
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    if (minLevel !== undefined) map.setMinLevel?.(minLevel)
    if (maxLevel !== undefined) map.setMaxLevel?.(maxLevel)
  }, [ready, minLevel, maxLevel])

  // 확대 수준이 바뀌면 알린다. 범위 제한이 없는 SDK 에서 범위를 넘으면 경계값으로 되돌린다
  const watchesLevel = onLevelChange !== undefined || minLevel !== undefined || maxLevel !== undefined
  useEffect(() => {
    const maps = mapsRef.current
    const map = mapRef.current
    if (!ready || !maps || !map || !watchesLevel) return
    const handleZoom = () => {
      const { minLevel: lo, maxLevel: hi } = rangeRef.current
      const current = map.getLevel()
      const clamped = Math.min(Math.max(current, lo ?? current), hi ?? current)
      // 되돌리면 zoom_changed 가 한 번 더 오므로 그때 알린다
      if (clamped !== current) map.setLevel(clamped)
      else onLevelChangeRef.current?.(current)
    }
    maps.event.addListener(map, 'zoom_changed', handleZoom)
    onLevelChangeRef.current?.(map.getLevel())
    return () => maps.event.removeListener(map, 'zoom_changed', handleZoom)
  }, [ready, watchesLevel])

  // 지도가 준비되면 바깥에 손잡이를 넘긴다
  useEffect(() => {
    const maps = mapsRef.current
    const map = mapRef.current
    const container = containerRef.current
    if (!ready || !maps || !map || !container) return
    onReadyRef.current?.({
      zoomBy: (delta) => {
        const { minLevel: lo, maxLevel: hi } = rangeRef.current
        const current = map.getLevel()
        const next = Math.min(Math.max(current + delta, lo ?? current + delta), hi ?? current + delta)
        if (next !== current) map.setLevel(next, { animate: true })
      },
      panToVisible: (target, targetY) => {
        // 지금 보이는 범위로 화면 1px 당 위도를 구해, 좌표가 targetY 에 오도록 중심을 그만큼 남북으로 옮긴다
        const bounds = map.getBounds()
        const height = container.clientHeight
        const latPerPx = (bounds.getNorthEast().getLat() - bounds.getSouthWest().getLat()) / height
        const dy = height / 2 - targetY
        map.panTo(new maps.LatLng(target.lat - dy * latPerPx, target.lng))
      },
      fitCircle: (center, radius, padding, maxLevel) => {
        // 원을 감싸는 사각형(남서·북동)으로 범위를 맞춘다. 남는 공간이 너무 작으면 여백 없이 맞춘다
        const dLat = radius / METERS_PER_DEGREE
        const dLng = radius / (METERS_PER_DEGREE * Math.cos((center.lat * Math.PI) / 180))
        const bounds = new maps.LatLngBounds()
        bounds.extend(new maps.LatLng(center.lat - dLat, center.lng - dLng))
        bounds.extend(new maps.LatLng(center.lat + dLat, center.lng + dLng))
        fitToBounds(map, bounds, padding, container)
        // 넓은 반경 때문에 과하게 축소하지 않는다. 범위 맞춤으로 정한 중심(원 중심이 보이는 영역 가운데)은 그대로 두고 레벨만 줄인다
        if (map.getLevel() > maxLevel) map.setLevel(maxLevel)
      },
    })
  }, [ready])

  // ── 클러스터 장소 마커 (주변 탐색 HOM-02) ──
  // 마커는 id 별로 한 번만 만들어 두고, 목록이 바뀌면 빠진 것만 빼고 새로 생긴 것만 더한다 (카테고리를 바꿔도 다시 만들지 않음)
  const clustererRef = useRef<KakaoMarkerClusterer | null>(null)
  // 지금 그려진 클러스터들. 현재 위치와 겹치는지 다시 볼 때 쓴다
  const clustersRef = useRef<KakaoCluster[]>([])
  // 현재 위치 스티커와 겹치는 클러스터는 링 + 숫자 배지로, 아닌 것은 원래 숫자 원으로 (좌표·크기는 그대로)
  const updateNearMeRef = useRef(() => {
    const me = containerRef.current?.querySelector<HTMLElement>('[aria-label="현재 위치"]')
    const meRect = me?.getBoundingClientRect()
    clustersRef.current.forEach((cluster) => {
      const content = cluster.getClusterMarker().getContent()
      if (!(content instanceof HTMLElement)) return
      const rect = content.getBoundingClientRect()
      // 화면 밖(그려지지 않음)이면 지금 모양을 그대로 둔다. 지도가 멈추면(idle) 다시 본다
      if (rect.width === 0) return
      let near = false
      if (meRect && meRect.width > 0) {
        const dx = rect.left + rect.width / 2 - (meRect.left + meRect.width / 2)
        const dy = rect.top + rect.height / 2 - (meRect.top + meRect.height / 2)
        // 숫자 원이 캐릭터가 든 흰 원(60px)에 닿으면 겹친 것으로 본다
        near = Math.hypot(dx, dy) < ME_RING_SIZE / 2 + rect.width / 2
      }
      styleClusterNearMe(content, near)
    })
  })
  const clusterMarkersRef = useRef(new Map<string, KakaoMarker>())
  const clusterClicksRef = useRef(new Map<string, () => void>())
  const placePinImageRef = useRef<KakaoMarkerImage | null | undefined>(undefined)
  useEffect(() => {
    const maps = mapsRef.current
    const map = mapRef.current
    if (!ready || !maps || !map || !clusterMarkers) return
    // 클러스터러 라이브러리를 못 불렀으면 묶지 않고 지도에 바로 올린다
    if (!clustererRef.current && maps.MarkerClusterer) {
      clustererRef.current = new maps.MarkerClusterer({
        map,
        averageCenter: true,
        minLevel: CLUSTER_MIN_LEVEL,
        gridSize: CLUSTER_GRID,
        // 누르면 한 단계 확대 (카카오 기본 동작). 같은 좌표만 남은 묶음은 끝까지 확대해도 풀리지 않는다 (좌표는 옮기지 않음)
        disableClickZoom: false,
        calculator: [10, 30],
        styles: CLUSTER_STYLES,
      })
      // 숫자 원은 현재 위치 스티커보다 위에 둔다 (가운데 큰 묶음이 스티커에 가려 개수가 안 보이는 것을 막음). 고른 장소 핀은 그보다 위
      // 클러스터를 다시 묶을 때마다(확대·축소·목록 변경) 현재 위치와 겹치는 묶음을 링 모양으로 바꾼다
      maps.event.addListener(clustererRef.current, 'clustered', (clusters) => {
        clusters.forEach((cluster) => cluster.getClusterMarker().setZIndex(Z_CLUSTER))
        clustersRef.current = clusters
        requestAnimationFrame(() => updateNearMeRef.current())
      })
    }
    const clusterer = clustererRef.current
    if (placePinImageRef.current === undefined) placePinImageRef.current = createPlacePinImage(maps)
    const image = placePinImageRef.current

    const current = clusterMarkersRef.current
    const nextIds = new Set(clusterMarkers.map((m) => m.id))
    const removed: KakaoMarker[] = []
    for (const [id, marker] of current) {
      if (!nextIds.has(id)) {
        removed.push(marker)
        current.delete(id)
      }
    }
    const added: KakaoMarker[] = []
    const clicks = new Map<string, () => void>()
    for (const m of clusterMarkers) {
      if (m.onClick) clicks.set(m.id, m.onClick)
      let marker = current.get(m.id)
      if (!marker) {
        const position = new maps.LatLng(m.lat, m.lng)
        marker = image ? new maps.Marker({ position, image }) : new maps.Marker({ position })
        const id = m.id
        maps.event.addListener(marker, 'click', () => clusterClicksRef.current.get(id)?.())
        current.set(id, marker)
        added.push(marker)
      }
      marker.setOpacity(m.dimmed ? DIMMED_OPACITY : 1)
    }
    clusterClicksRef.current = clicks
    if (clusterer) {
      // 한 번에 빼고 더한 뒤 한 번만 다시 그린다
      if (removed.length) clusterer.removeMarkers(removed, true)
      if (added.length) clusterer.addMarkers(added, true)
      clusterer.redraw()
    } else {
      removed.forEach((marker) => marker.setMap(null))
      added.forEach((marker) => marker.setMap(map))
    }
  }, [ready, clusterMarkers])

  // 지도를 끌어 클러스터가 새로 화면에 들어오거나 현재 위치가 다시 그려지면 겹침을 다시 본다
  const usesCluster = clusterMarkers !== undefined
  useEffect(() => {
    const maps = mapsRef.current
    const map = mapRef.current
    if (!ready || !maps || !map || !usesCluster) return
    const handleIdle = () => updateNearMeRef.current()
    maps.event.addListener(map, 'idle', handleIdle)
    return () => maps.event.removeListener(map, 'idle', handleIdle)
  }, [ready, usesCluster])
  useEffect(() => {
    if (!ready || !usesCluster) return
    const frame = requestAnimationFrame(() => updateNearMeRef.current())
    return () => cancelAnimationFrame(frame)
  }, [ready, usesCluster, markers])

  // 지도가 사라질 때 클러스터·마커 정리
  useEffect(() => {
    const markers = clusterMarkersRef.current
    return () => {
      clustererRef.current?.clear()
      markers.forEach((marker) => marker.setMap(null))
      markers.clear()
    }
  }, [])

  // ── 탐색 반경 원 ──
  // 원은 지도 도형이라 마커·지도 끌기를 막지 않는다. 중심·반지름이 바뀌면 같은 원을 고친다
  const circleRef = useRef<KakaoCircle | null>(null)
  const circleLat = circle?.center.lat
  const circleLng = circle?.center.lng
  const circleRadius = circle?.radius
  useEffect(() => {
    const maps = mapsRef.current
    const map = mapRef.current
    if (!ready || !maps || !map) return
    if (circleLat === undefined || circleLng === undefined || circleRadius === undefined) {
      circleRef.current?.setMap(null)
      circleRef.current = null
      return
    }
    const center = new maps.LatLng(circleLat, circleLng)
    if (!circleRef.current) {
      circleRef.current = new maps.Circle({ center, radius: circleRadius, ...CIRCLE_STYLE, zIndex: 0, map })
    } else {
      circleRef.current.setPosition(center)
      circleRef.current.setRadius(circleRadius)
    }
  }, [ready, circleLat, circleLng, circleRadius])
  useEffect(
    () => () => {
      circleRef.current?.setMap(null)
    },
    [],
  )

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
