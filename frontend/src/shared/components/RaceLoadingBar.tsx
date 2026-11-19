import { useEffect, useRef, useState } from 'react'
import StickerImage from './StickerImage'

/*
 * [이 파일이 하는 일]
 * 토끼·거북이 달리기 로딩 바. 스플래시(Splash)와 공통 로딩 화면(LoadingState)이 같이 쓴다.
 *   트랙: S자로 길게 (왼쪽 위 출발 → 오른쪽으로 돌아 → 왼쪽으로 돌아 → 오른쪽 아래 도착)
 *   마커: 지도에 찍는 핀 스티커 S · 1 · 2 + 도착 깃발 (코스 지도와 같은 그림)
 *   토끼: 빠르게 콩콩콩 1 까지 → 쉼(z Z z zZ) → 콩콩콩 2 까지 → 쉼 → 콩콩콩 깃발
 *   거북이: 처음부터 아주 느리게 같은 속도로 따라온다 (도착 안 해도 됨). 채워지는 트랙은 토끼를 따라간다
 *
 *   <RaceLoadingBar readyAt={null} />       화면 준비 중: 2 까지 가서 계속 잔다
 *   <RaceLoadingBar readyAt={준비된 시각} />  2 도착(2초)과 준비 완료 중 늦은 때부터 1초 동안 깃발로 → 화면 전환
 *   시간표는 아래 '시간' 주석. 화면 전환 시각 계산은 useRaceLoading 이 같은 상수로 한다
 */

// ── 트랙 모양 (SVG 좌표, viewBox 300×180) ──
const VIEW_W = 300
const VIEW_H = 180
const R = 30 // 굽은 곳 반지름
type Seg = { len: number; at: (k: number) => [number, number] }
const SEGMENTS: Seg[] = [
  { len: 220, at: (k) => [30 + 220 * k, 30] }, // 위 줄 →
  { len: Math.PI * R, at: (k) => { const a = -Math.PI / 2 + Math.PI * k; return [250 + R * Math.cos(a), 60 + R * Math.sin(a)] } }, // 오른쪽 굽이
  { len: 200, at: (k) => [250 - 200 * k, 90] }, // 가운데 줄 ←
  { len: Math.PI * R, at: (k) => { const a = -Math.PI / 2 - Math.PI * k; return [50 + R * Math.cos(a), 120 + R * Math.sin(a)] } }, // 왼쪽 굽이
  { len: 220, at: (k) => [50 + 220 * k, 150] }, // 아래 줄 →
]
const TRACK_LEN = SEGMENTS.reduce((sum, s) => sum + s.len, 0)
const TRACK_PATH = `M30 30 H250 A${R} ${R} 0 0 1 250 90 H50 A${R} ${R} 0 0 0 50 150 H270`

/** 트랙 위 위치(0~1) → SVG 좌표 */
function pointAt(p: number): [number, number] {
  let d = Math.min(1, Math.max(0, p)) * TRACK_LEN
  for (const s of SEGMENTS) {
    if (d <= s.len) return s.at(d / s.len)
    d -= s.len
  }
  return SEGMENTS[SEGMENTS.length - 1].at(1)
}

/** 마커 1·2 = 오른쪽·왼쪽 굽이 한가운데. 토끼가 여기서 쉰다 */
const MARK_1 = (220 + (Math.PI * R) / 2) / TRACK_LEN
const MARK_2 = (220 + Math.PI * R + 200 + (Math.PI * R) / 2) / TRACK_LEN

/** 토끼가 실제로 쉬는 자리: 핀 바로 다음 (핀 위에 앉으면 숫자가 가려진다) */
const STOP_1 = MARK_1 + 0.035
const STOP_2 = MARK_2 + 0.035

// ── 시간 (ms) ── 시간표 (토끼 기준)
//   0 ~ 1초  : S → 1 (0.8초 콩콩) 도착, 잠깐 쉼
//   1 ~ 2초  : 1 → 2 콩콩, 2초에 2 도착 → 여기서 "화면 준비됐나" 확인
//   준비됨    : 그때부터 1초 = 깃발까지 0.7초 콩콩 + 깃발 앞 0.3초 → 화면 전환 (빠르면 3초에 전환)
//   준비 안 됨: 2 에서 계속 잔다 (z Z z zZ). 준비되는 순간부터 위와 같은 1초
const HOP_1 = 800
const REST_1 = 400
const HOP_2 = 800
/** 2 에 도착하는 시각 = 준비 확인 시각 */
export const READY_CHECK_MS = HOP_1 + REST_1 + HOP_2
/** 2 → 깃발 콩콩 */
const FINAL_RUN_MS = 700
/** 준비된 뒤 화면 전환까지 (깃발까지 달리기 + 깃발 앞 콩) */
export const FINISH_TOTAL_MS = 1000
/** 거북이 빠르기: 트랙 전체를 이만큼에 (아주 느리게) */
const TURTLE_FULL_MS = 9000
/** 한 구간에 콩콩 뛰는 횟수 */
const HOPS_PER_LEG = 3

/** 시작 후 e(ms)에서 토끼 위치·깡충 높이(0~1)·자는 중. finalStart = 2 에서 깨서 뛰기 시작하는 시각(없으면 계속 잠) */
function rabbitAt(e: number, finalStart: number | null): { p: number; hop: number; sleeping: boolean } {
  const hop = (k: number) => Math.abs(Math.sin(k * Math.PI * HOPS_PER_LEG))
  if (e < HOP_1) return { p: (STOP_1 * e) / HOP_1, hop: hop(e / HOP_1), sleeping: false }
  if (e < HOP_1 + REST_1) return { p: STOP_1, hop: 0, sleeping: true }
  const leg2 = e - HOP_1 - REST_1
  if (leg2 < HOP_2) return { p: STOP_1 + ((STOP_2 - STOP_1) * leg2) / HOP_2, hop: hop(leg2 / HOP_2), sleeping: false }
  if (finalStart === null || e < finalStart) return { p: STOP_2, hop: 0, sleeping: true }
  const k = (e - finalStart) / FINAL_RUN_MS
  if (k < 1) return { p: STOP_2 + (1 - STOP_2) * k, hop: hop(k), sleeping: false }
  // 도착: 깃발 앞에서 제자리 콩 (남은 시간 동안 한 번)
  const after = (e - finalStart - FINAL_RUN_MS) / (FINISH_TOTAL_MS - FINAL_RUN_MS)
  return { p: 1, hop: after < 1 ? Math.abs(Math.sin(after * Math.PI)) : 0, sleeping: false }
}

const reducedMotion = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/** SVG 좌표 → 상자 안 위치 (%) */
const posStyle = ([x, y]: [number, number]) => ({ left: `${(x / VIEW_W) * 100}%`, top: `${(y / VIEW_H) * 100}%` })

// 지도 핀 스티커 기준점 (KakaoMap 과 같은 값, 512px 원본 기준)
const PIN_TIP = { x: 254 / 512, y: 452 / 512 }
const PIN_CIRCLE = { x: 252 / 512, y: 215 / 512 }
const FLAG_BASE = { x: 135 / 512, y: 449 / 512 }

export default function RaceLoadingBar({
  readyAt = null,
  sizeClass = 'w-[min(78vw,320px)]',
  className = '',
}: {
  /** 화면 준비가 끝난 시각 (Date.now() 기준 ms). null 이면 아직 준비 중 → 토끼가 2 에서 기다린다 */
  readyAt?: number | null
  /** 트랙 너비 (높이는 비율로 따라감). 스플래시처럼 자리가 좁으면 줄인다 */
  sizeClass?: string
  className?: string
}) {
  // 처음 그린 시각과 지금까지 흐른 시간(ms). 매 프레임 갱신
  const [startAt] = useState(() => Date.now())
  const [elapsed, setElapsed] = useState(0)
  const readyRef = useRef(readyAt)
  useEffect(() => {
    readyRef.current = readyAt
  }, [readyAt])

  useEffect(() => {
    let frame = 0
    const tick = () => {
      const e = Date.now() - startAt
      setElapsed(e)
      const ready = readyRef.current
      // 화면 전환 시각까지 그리고 멈춘다 (거북이는 그 자리에 둔다)
      if (ready !== null && e >= Math.max(READY_CHECK_MS, ready - startAt) + FINISH_TOTAL_MS) return
      frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [startAt])

  // 2 에서 깨는 시각: 2 에 도착(2초)했고 화면도 준비됐을 때. 둘 중 늦은 쪽
  const finalStart = readyAt === null ? null : Math.max(READY_CHECK_MS, readyAt - startAt)
  const rabbit = rabbitAt(elapsed, finalStart)
  const turtleP = Math.min(1, elapsed / TURTLE_FULL_MS)
  const hopPx = reducedMotion() ? 0 : rabbit.hop * 12
  const [rx, ry] = pointAt(rabbit.p)

  return (
    <div aria-hidden className={`relative aspect-[300/180] shrink-0 ${sizeClass} ${className}`}>
      {/* 트랙: 잉크 테두리 → 크림 길 → 토끼까지 핑크로 채움 */}
      <svg viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} className="absolute inset-0 size-full overflow-visible">
        <path d={TRACK_PATH} fill="none" stroke="var(--color-ink)" strokeWidth={15} strokeLinecap="round" strokeLinejoin="round" />
        <path d={TRACK_PATH} fill="none" stroke="var(--color-cream)" strokeWidth={10} strokeLinecap="round" strokeLinejoin="round" />
        <path
          d={TRACK_PATH}
          fill="none"
          stroke="var(--color-primary)"
          strokeWidth={10}
          strokeLinecap="round"
          strokeDasharray={TRACK_LEN}
          strokeDashoffset={TRACK_LEN * (1 - rabbit.p)}
          className={rabbit.p === 0 ? 'opacity-0' : undefined}
        />
      </svg>

      {/* 마커: 지도 핀 스티커(S·1·2) + 도착 깃발. 핀 끝이 트랙 위에 오게 */}
      {[
        { at: 0, label: 'S' },
        { at: MARK_1, label: '1' },
        { at: MARK_2, label: '2' },
      ].map((m) => (
        <div
          key={m.label}
          className="absolute size-11"
          style={{ ...posStyle(pointAt(m.at)), transform: `translate(${-PIN_TIP.x * 100}%, ${-PIN_TIP.y * 100}%)` }}
        >
          <StickerImage name="mapPin" className="size-full" />
          <span
            className="absolute -translate-x-1/2 -translate-y-1/2 font-display text-sm leading-none text-ink"
            style={{ left: `${PIN_CIRCLE.x * 100}%`, top: `${PIN_CIRCLE.y * 100}%` }}
          >
            {m.label}
          </span>
        </div>
      ))}
      <div
        className="absolute size-11"
        style={{ ...posStyle(pointAt(1)), transform: `translate(${-FLAG_BASE.x * 100}%, ${-FLAG_BASE.y * 100}%)` }}
      >
        <StickerImage name="mapFlag" className="size-full" />
      </div>

      {/* 거북이 (뒤): 아주 느리게 */}
      <div className="absolute z-10" style={{ ...posStyle(pointAt(turtleP)), transform: 'translate(-50%, -85%)' }}>
        <div className="splash-turtle-body origin-bottom">
          <StickerImage name="turtleHead" className="size-9" />
        </div>
      </div>

      {/* 토끼 (앞): 콩콩콩, 1·2 에서 쉬는 동안 z Z z zZ */}
      <div className="absolute z-20" style={{ left: `${(rx / VIEW_W) * 100}%`, top: `${(ry / VIEW_H) * 100}%`, transform: 'translate(-50%, -85%)' }}>
        <div className="relative">
          {rabbit.sleeping && (
            <span className="absolute -top-5 left-[75%] flex font-display text-sky">
              {['z', 'Z', 'z', 'zZ'].map((z, i) => (
                <span key={i} className="race-zzz text-sm" style={{ animationDelay: `${i * 0.25}s` }}>
                  {z}
                </span>
              ))}
            </span>
          )}
          <div
            className="origin-bottom"
            style={{ transform: rabbit.sleeping ? 'translateY(3px) rotate(-18deg)' : `translateY(${-hopPx}px) rotate(${-hopPx * 0.6}deg)` }}
          >
            <StickerImage name="rabbitHead" className="size-11" />
          </div>
        </div>
      </div>
    </div>
  )
}
