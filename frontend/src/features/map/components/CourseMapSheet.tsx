import { useEffect, useRef, useState, type MouseEvent, type PointerEvent } from 'react'
import { PAPER_STYLE } from '../../../shared/assets/stickerAssets'
import Icon from '../../../shared/components/Icon'
import { formatMinutes } from '../../../shared/utils/format'
import { buildSchedule, getLegMode, getTotalTravelMinutes, parseTransitInfo, type Course, type CoursePlace } from '../../recommend/types'

/*
 * [이 파일이 하는 일]
 * 코스 지도(DET-02) 아래 코스 상세 패널. 지도 위에 겹쳐 올라오고 접힘 / 펼침 2단계다.
 * - 접힘(기본): 총 시간 + 지점 요약(S → 1 → 2 → E). 지점을 고르면 그 장소 카드 (방문 시각·체류시간)
 * - 펼침: 전체 일정 (방문 시각·체류시간, 구간별 이동수단·버스·지하철 노선)
 * 손잡이·제목 줄을 누르면 접힘 ↔ 펼침, 위아래로 끌면 손가락을 따라오다 놓으면 가까운 단계로 정착한다.
 * 지점 id 는 CourseMap 마커와 같다: 출발 'S', 장소 visitOrder('1'…), 도착 'E'
 */

export type CourseSheetLevel = 'collapsed' | 'expanded'

// 높이는 CourseMap 의 지도 영역(relative)을 기준으로 한다. Tailwind 가 찾을 수 있게 클래스 문자열을 조합하지 않는다
const SHEET_HEIGHT_CLASS: Record<CourseSheetLevel, string> = {
  collapsed: 'h-44',
  expanded: 'h-[calc(100%-3rem)]',
}
/** 접힘 높이(px) = h-44 (11rem) */
const COLLAPSED_PX = 176
/** 펼침일 때 위에 남기는 지도 높이(px) = 3rem */
const EXPANDED_TOP_GAP_PX = 48
/** 이만큼(px) 이하로 움직이면 끌기가 아니라 누름 */
const TAP_SLOP = 8
/** 이만큼(px) 이상 끌고 놓으면 끈 방향 단계로 */
const STEP_THRESHOLD = 40

interface DragState {
  pointerId: number
  startY: number
  startHeight: number
  parentHeight: number
  moved: boolean
}

interface CourseMapSheetProps {
  course: Course
  level: CourseSheetLevel
  onLevelChange: (level: CourseSheetLevel) => void
  /** 고른 지점 id ('S', '1'…, 'E'). 없으면 null */
  selectedId: string | null
  /** 목록에서 지점을 눌렀을 때 */
  onSelect: (id: string) => void
  /** 고른 장소 카드의 [장소 상세] (카카오 장소 정보 시트를 연다) */
  onOpenPlace: () => void
}

export default function CourseMapSheet({ course, level, onLevelChange, selectedId, onSelect, onOpenPlace }: CourseMapSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLOListElement>(null)
  const drag = useRef<DragState | null>(null)
  // 끌기 중 높이(px). null 이면 단계 높이를 쓴다
  const [dragHeight, setDragHeight] = useState<number | null>(null)

  const { rows, endTime } = buildSchedule(course)
  const selectedPlace = course.places.find((p) => String(p.visitOrder) === selectedId) ?? null
  const toggle = () => onLevelChange(level === 'collapsed' ? 'expanded' : 'collapsed')

  // 펼친 목록에서 고른 지점이 안 보이면 보이게 스크롤
  useEffect(() => {
    if (level !== 'expanded' || !selectedId) return
    listRef.current?.querySelector(`[data-stop-id="${CSS.escape(selectedId)}"]`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [level, selectedId])

  const clampHeight = (height: number, parentHeight: number) =>
    Math.min(Math.max(height, COLLAPSED_PX), Math.max(COLLAPSED_PX, parentHeight - EXPANDED_TOP_GAP_PX))

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (!e.isPrimary || (e.pointerType === 'mouse' && e.button !== 0)) return
    const sheet = sheetRef.current
    if (!sheet) return
    drag.current = {
      pointerId: e.pointerId,
      startY: e.clientY,
      startHeight: sheet.getBoundingClientRect().height,
      parentHeight: sheet.parentElement?.clientHeight ?? 0,
      moved: false,
    }
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const d = drag.current
    if (!d || d.pointerId !== e.pointerId) return
    const dy = e.clientY - d.startY
    if (!d.moved && Math.abs(dy) <= TAP_SLOP) return
    d.moved = true
    setDragHeight(clampHeight(d.startHeight - dy, d.parentHeight))
  }
  const onPointerUp = (e: PointerEvent<HTMLDivElement>) => {
    const d = drag.current
    if (!d || d.pointerId !== e.pointerId) return
    drag.current = null
    setDragHeight(null)
    const dy = e.clientY - d.startY
    if (!d.moved) {
      toggle()
      return
    }
    if (dy <= -STEP_THRESHOLD) onLevelChange('expanded')
    else if (dy >= STEP_THRESHOLD) onLevelChange('collapsed')
  }
  const onPointerCancel = () => {
    drag.current = null
    setDragHeight(null)
  }
  // 키보드(Enter·Space)로 누른 경우만. 포인터 누름은 onPointerUp 에서 처리 (detail = 0 이면 키보드)
  const onHandleClick = (e: MouseEvent<HTMLButtonElement>) => {
    if (e.detail === 0) toggle()
  }

  return (
    <div
      ref={sheetRef}
      style={{ ...PAPER_STYLE, ...(dragHeight === null ? {} : { height: dragHeight }) }}
      className={`absolute inset-x-0 bottom-0 z-20 flex flex-col overflow-hidden rounded-t-[28px] border-2 border-b-0 border-line bg-cream shadow-[0_-4px_16px_rgba(0,0,0,0.08)] ${
        dragHeight === null ? 'transition-[height] duration-200' : ''
      } ${SHEET_HEIGHT_CLASS[level]}`}
    >
      {/* 끌기·누르기 영역: 손잡이 + 총 시간 줄 */}
      <div
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
        className="shrink-0 cursor-grab touch-none select-none active:cursor-grabbing"
      >
        <button
          type="button"
          aria-label={level === 'collapsed' ? '코스 일정 펼치기' : '코스 일정 접기'}
          aria-expanded={level === 'expanded'}
          onClick={onHandleClick}
          className="flex h-6 w-full items-center justify-center"
        >
          <span className="h-1 w-10 rounded-full bg-neutral-300" />
        </button>
        <div className="flex items-center gap-2 px-5 pb-2">
          <span className="flex items-center gap-1 rounded-full border-2 border-ink bg-sunny px-2.5 py-0.5 text-sm font-bold">
            <Icon name="clock" className="size-4" strokeWidth={2.6} />총 {formatMinutes(course.totalMinutes)}
          </span>
          <span className="text-xs text-neutral-500">이동 {formatMinutes(getTotalTravelMinutes(course))}</span>
          <span className="ml-auto text-xs text-neutral-500">{level === 'collapsed' ? '일정 펼치기 ▲' : '접기 ▼'}</span>
        </div>
      </div>

      {level === 'collapsed' ? (
        <div className="min-h-0 flex-1 px-4 pb-3">
          {selectedPlace ? (
            <SelectedPlaceCard place={selectedPlace} time={rows.find((r) => r.place === selectedPlace)} onOpen={onOpenPlace} />
          ) : (
            <StopSummary course={course} onSelect={onSelect} />
          )}
        </div>
      ) : (
        <ol ref={listRef} className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
          <StopRow id="S" label="S" dark name={`출발 · ${course.origin.name}`} sub={course.startTime} selected={selectedId === 'S'} onSelect={onSelect} />
          {rows.map(({ place, arrive, leave }) => {
            const id = String(place.visitOrder)
            return (
              <li key={place.placeId}>
                <LegLine course={course} minutes={place.travelMinutes} transitInfo={place.transitInfo} />
                <StopRow
                  id={id}
                  label={id}
                  name={place.name}
                  sub={`${arrive} ~ ${leave} · 체류 ${formatMinutes(place.stayMinutes)}`}
                  selected={selectedId === id}
                  onSelect={onSelect}
                  asItem={false}
                />
              </li>
            )
          })}
          {course.destination ? (
            <li>
              <LegLine course={course} minutes={course.finalTravelMinutes} />
              <StopRow id="E" label="E" dark name={`도착 · ${course.destination.name}`} sub={endTime} selected={selectedId === 'E'} onSelect={onSelect} asItem={false} />
            </li>
          ) : (
            <li className="mt-2 px-2 text-xs text-neutral-500">코스 종료 {endTime}</li>
          )}
        </ol>
      )}
    </div>
  )
}

/** 접힘: 지점 요약. 가로로 넘겨 본다 */
function StopSummary({ course, onSelect }: { course: Course; onSelect: (id: string) => void }) {
  const stops = [
    { id: 'S', label: 'S', name: course.origin.name, dark: true },
    ...course.places.map((p) => ({ id: String(p.visitOrder), label: String(p.visitOrder), name: p.name, dark: false })),
    ...(course.destination ? [{ id: 'E', label: 'E', name: course.destination.name, dark: true }] : []),
  ]
  return (
    <div className="flex h-full flex-col justify-center gap-2">
      <ol className="flex items-center gap-1 overflow-x-auto pb-1">
        {stops.map((s, i) => (
          <li key={s.id} className="flex shrink-0 items-center gap-1">
            {i > 0 && <Icon name="chevronRight" className="size-3.5 text-neutral-400" />}
            <button
              type="button"
              onClick={() => onSelect(s.id)}
              className="flex max-w-36 items-center gap-1.5 rounded-full border-2 border-line bg-white/70 py-1 pr-2.5 pl-1 text-xs active:border-primary"
            >
              <Badge label={s.label} dark={s.dark} small />
              <span className="truncate">{s.name}</span>
            </button>
          </li>
        ))}
      </ol>
      <p className="px-1 text-[11px] text-neutral-400">지도 번호나 장소를 누르면 그 위치로 이동해요</p>
    </div>
  )
}

/** 접힘: 고른 장소 카드 */
function SelectedPlaceCard({ place, time, onOpen }: { place: CoursePlace; time?: { arrive: string; leave: string }; onOpen: () => void }) {
  return (
    <div className="paper-card flex items-center gap-3 p-3">
      <Badge label={String(place.visitOrder)} />
      <div className="min-w-0 flex-1">
        <p className="truncate font-bold">{place.name}</p>
        <p className="text-[11px] text-neutral-500">
          {time ? `${time.arrive} ~ ${time.leave} · ` : ''}체류 {formatMinutes(place.stayMinutes)}
        </p>
        <p className="truncate text-[11px] text-neutral-400">{place.address}</p>
      </div>
      <button
        type="button"
        onClick={onOpen}
        className="flex shrink-0 items-center gap-0.5 rounded-full border-2 border-ink bg-primary px-3 py-1.5 text-xs font-bold text-white"
      >
        장소 상세
        <Icon name="chevronRight" className="size-3.5" strokeWidth={2.8} />
      </button>
    </div>
  )
}

interface StopRowProps {
  id: string
  label: string
  name: string
  sub: string
  selected: boolean
  onSelect: (id: string) => void
  dark?: boolean
  /** false 면 <li> 로 감싸지 않는다 (구간 줄과 함께 바깥 <li> 안에 있을 때) */
  asItem?: boolean
}

/** 펼침: 지점 1줄 */
function StopRow({ id, label, name, sub, selected, onSelect, dark = false, asItem = true }: StopRowProps) {
  const body = (
    <div data-stop-id={id} className={`rounded-xl ${selected ? 'bg-primary-light/60' : ''}`}>
      <button type="button" onClick={() => onSelect(id)} className="flex w-full items-center gap-3 px-2 py-2 text-left">
        <Badge label={label} dark={dark} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-bold">{name}</span>
          <span className="block text-[11px] text-neutral-500">{sub}</span>
        </span>
        <Icon name="location" className="size-4 shrink-0 text-neutral-400" />
      </button>
    </div>
  )
  return asItem ? <li>{body}</li> : body
}

/**
 * 펼침: 지점 사이 이동 구간 (코스 상세 CourseSchedule 의 Leg 와 같은 문구, #156)
 * 예) "버스+지하철 · 버스 3411 → 수도권 2호선 · 25분 · 환승 1회", "도보 8분"
 */
function LegLine({ course, minutes, transitInfo }: { course: Course; minutes: number; transitInfo?: string }) {
  const mode = getLegMode(course, transitInfo)
  const { lines, transfer } = transitInfo ? parseTransitInfo(transitInfo) : { lines: [], transfer: null }
  const text = lines.length > 0 ? [mode, lines.join(' → '), formatMinutes(minutes), transfer].filter(Boolean).join(' · ') : `${mode} ${formatMinutes(minutes)}`
  return (
    <p className="my-0.5 ml-[21px] border-l-2 border-dashed border-primary/50 py-1 pl-5 text-[11px] text-neutral-500">
      {text}
    </p>
  )
}

/** 번호 원 (장소: 핑크, 출발·도착: 노랑) */
function Badge({ label, dark = false, small = false }: { label: string; dark?: boolean; small?: boolean }) {
  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-full border-2 border-ink font-display ${small ? 'size-5 text-[11px]' : 'size-7 text-sm'} ${
        dark ? 'bg-sunny text-ink' : 'bg-primary text-white'
      }`}
    >
      {label}
    </span>
  )
}
