import { useEffect, useRef, useState, type MouseEvent, type PointerEvent } from 'react'
import { Link } from 'react-router'
import Chip from '../../../shared/components/Chip'
import ChipGroup from '../../../shared/components/ChipGroup'
import EmptyState from '../../../shared/components/EmptyState'
import { CATEGORIES, getCategoryIcon, getCategoryLabel, type CategoryCode } from '../../../shared/types/domain'
import { formatDistance, walkMinutes } from '../format'
import type { NearbyPlace } from '../mock'
import { clampSheetHeight, SHEET_HEIGHT_CLASS, snapSheetLevel, tapSheetLevel, type SheetLevel } from '../sheetLayout'

const RADIUS_OPTIONS = [500, 1000, 3000]

/** 이만큼(px) 이하로 움직이면 끌기가 아니라 누름으로 본다 */
const TAP_SLOP = 8
/** 놓은 높이에서 가장 가까운 단계가 처음 단계여도, 이만큼(px) 이상 끌었으면 끈 방향으로 한 단계 옮긴다 */
const STEP_THRESHOLD = 40
/** 마커를 누른 뒤 이 시간(ms) 안에 끝나는 시트 높이 애니메이션 후에 카드 위치를 다시 맞춘다 */
const RESCROLL_WINDOW_MS = 1000

interface DragState {
  pointerId: number
  startY: number
  /** 끌기 시작 때 시트 높이(px) */
  startHeight: number
  /** 시트를 담은 지도 영역 높이(px) */
  parentHeight: number
  /** TAP_SLOP 을 넘게 움직였으면 true (누름이 아니라 끌기) */
  moved: boolean
}

interface NearbySheetProps {
  radius: number
  onRadiusChange: (radius: number) => void
  /** 대분류 필터 (null = 전체). 지도 마커와 같은 값을 쓰도록 HomeScreen 이 관리한다 */
  category: CategoryCode | null
  onCategoryChange: (category: CategoryCode | null) => void
  /** 반경·대분류로 거른 장소 목록 (HomeScreen 에서 계산) */
  places: NearbyPlace[]
  /** 지도 마커로 고른 장소. 해당 카드를 강조하고 목록에서 보이게 스크롤한다 */
  selectedPlaceId: string | null
  /** 마커를 누를 때마다 바뀌는 값. 같은 마커를 다시 눌러도 해당 카드로 다시 스크롤한다 */
  scrollRequest: number
  /** true면 대분류와 상관없이 반경 안에 장소가 하나도 없다 (빈 목록 안내 문구 구분용) */
  radiusEmpty: boolean
  /** 시트 높이 단계. 지도 영역도 같은 값으로 줄이도록 HomeScreen 이 관리한다 */
  level: SheetLevel
  onLevelChange: (level: SheetLevel) => void
}

/**
 * 주변 장소 탐색 바텀시트 (HOM-02): 접힘 / 반만(기본) / 전체 3단계.
 * - 손잡이·'내 주변 장소' 제목 줄을 누르면 반만 ↔ 접힘 (전체에서는 반만)
 * - 끌면 시트가 손가락을 따라 움직이고, 놓으면 가까운 단계로 정착한다 (길게 끌면 두 단계도 한 번에)
 * 끝까지 내려도 홈으로 가지 않고 접힘 상태로 주변 탐색을 유지한다.
 * 장소 카드를 누르면 장소 1곳짜리 상세(DET-01)로 이동한다. 마커를 누르면 해당 카드를 강조한다.
 * 장소 데이터는 HomeScreen 에서 받는다 (주변 장소 API 연동 TODO 는 HomeScreen 참고)
 */
export default function NearbySheet({
  radius,
  onRadiusChange,
  category,
  onCategoryChange,
  places,
  selectedPlaceId,
  scrollRequest,
  radiusEmpty,
  level,
  onLevelChange,
}: NearbySheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const drag = useRef<DragState | null>(null)
  // 끌기 중 시트 높이(px). null 이면 단계(level) 높이를 쓴다
  const [dragHeight, setDragHeight] = useState<number | null>(null)

  // 마커로 고른 카드가 목록 밖에 있으면 보이는 위치까지 스크롤한다.
  // 접힘에서 펼쳐지는 중이면 목록 높이가 아직 작으므로, 높이 애니메이션이 끝나면(transitionend) 한 번 더 맞춘다
  useEffect(() => {
    if (!selectedPlaceId) return
    const sheet = sheetRef.current
    const scroll = () => {
      const card = listRef.current?.querySelector(`[data-place-id="${CSS.escape(selectedPlaceId)}"]`)
      card?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    }
    const onTransitionEnd = (e: TransitionEvent) => {
      if (e.target === sheet && e.propertyName === 'height') scroll()
    }
    scroll()
    sheet?.addEventListener('transitionend', onTransitionEnd)
    const timer = setTimeout(() => sheet?.removeEventListener('transitionend', onTransitionEnd), RESCROLL_WINDOW_MS)
    return () => {
      clearTimeout(timer)
      sheet?.removeEventListener('transitionend', onTransitionEnd)
    }
  }, [selectedPlaceId, scrollRequest])

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (!e.isPrimary || (e.pointerType === 'mouse' && e.button !== 0)) return
    // 반경 선택 상자는 끌기·누르기 영역에서 뺀다
    if (e.target instanceof Element && e.target.closest('select')) return
    const sheet = sheetRef.current
    if (!sheet) return
    drag.current = {
      pointerId: e.pointerId,
      startY: e.clientY,
      startHeight: sheet.getBoundingClientRect().height,
      parentHeight: sheet.parentElement?.clientHeight ?? 0,
      moved: false,
    }
    // 영역 밖으로 끌어도 pointermove·pointerup 을 계속 받도록 잡아 둔다
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const d = drag.current
    if (!d || d.pointerId !== e.pointerId) return
    const dy = e.clientY - d.startY
    if (!d.moved && Math.abs(dy) <= TAP_SLOP) return
    d.moved = true
    setDragHeight(clampSheetHeight(d.startHeight - dy, d.parentHeight))
  }
  const onPointerUp = (e: PointerEvent<HTMLDivElement>) => {
    const d = drag.current
    if (!d || d.pointerId !== e.pointerId) return
    drag.current = null
    setDragHeight(null)
    const dy = e.clientY - d.startY
    if (!d.moved && Math.abs(dy) <= TAP_SLOP) {
      // 거의 안 움직였으면 누름. 짧게 아래로 끈 것은 여기 오지 않는다 (끌기로 처리)
      onLevelChange(tapSheetLevel(level))
      return
    }
    onLevelChange(snapSheetLevel(level, d.startHeight, dy, d.parentHeight, STEP_THRESHOLD))
  }
  const onPointerCancel = () => {
    drag.current = null
    setDragHeight(null)
  }
  // 키보드(Enter·Space)로 손잡이를 누른 경우만 처리한다. 포인터 누름은 onPointerUp 에서 처리 (detail = 0 이면 키보드)
  const onHandleClick = (e: MouseEvent<HTMLButtonElement>) => {
    if (e.detail === 0) onLevelChange(tapSheetLevel(level))
  }

  return (
    <div
      ref={sheetRef}
      style={dragHeight === null ? undefined : { height: dragHeight }}
      className={`absolute inset-x-0 bottom-0 z-20 flex flex-col overflow-hidden rounded-t-2xl bg-white shadow-[0_-4px_16px_rgba(0,0,0,0.08)] ${
        dragHeight === null ? 'transition-[height] duration-200' : ''
      } ${SHEET_HEIGHT_CLASS[level]}`}
    >
      {/* 끌기·누르기 영역: 손잡이 + '내 주변 장소' 제목 줄 (반경 선택 상자는 제외) */}
      <div
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
        className="shrink-0 cursor-grab touch-none select-none active:cursor-grabbing"
      >
        <button
          type="button"
          aria-label={level === 'collapsed' ? '주변 장소 목록 펼치기' : '주변 장소 목록 접기'}
          aria-expanded={level !== 'collapsed'}
          onClick={onHandleClick}
          className="flex h-7 w-full items-center justify-center"
        >
          <span className="h-1 w-10 rounded-full bg-neutral-300" />
        </button>

        <div className="flex h-10 items-center justify-between px-4 pb-2">
          <h2 className="font-bold">내 주변 장소</h2>
          {level === 'collapsed' ? (
            <span className="text-xs text-neutral-500">펼치기 ▲</span>
          ) : (
            <select
              value={radius}
              onChange={(e) => onRadiusChange(Number(e.target.value))}
              aria-label="반경"
              className="h-8 cursor-pointer rounded-lg border border-line px-2 text-xs"
            >
              {RADIUS_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  반경 {formatDistance(r)}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      <div className="px-4 pb-2">
        <ChipGroup>
          <Chip selected={category === null} onClick={() => onCategoryChange(null)}>
            전체
          </Chip>
          {CATEGORIES.map((c) => (
            <Chip key={c.code} selected={category === c.code} onClick={() => onCategoryChange(c.code)}>
              {c.label}
            </Chip>
          ))}
        </ChipGroup>
      </div>

      {/* 목록만 스크롤된다. 끝까지 스크롤해도 바깥(페이지)으로 넘기지 않는다. 시트 끌기는 위 손잡이·제목 줄에서만 */}
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-4">
        {places.length === 0 ? (
          <EmptyState
            message={
              radiusEmpty ? '이 반경에 장소가 없어요. 반경을 넓혀보세요.' : '이 카테고리의 장소가 없어요. 다른 카테고리를 골라보세요.'
            }
          />
        ) : (
          <ul ref={listRef} className="flex flex-col gap-2">
            {places.map((place) => (
              <li key={place.placeId} data-place-id={place.placeId}>
                <Link
                  to={`/results/place-${place.placeId}`}
                  aria-current={place.placeId === selectedPlaceId ? 'true' : undefined}
                  className={`flex gap-3 rounded-xl border p-3 ${
                    place.placeId === selectedPlaceId ? 'border-primary bg-primary-light' : 'border-line'
                  }`}
                >
                  <div aria-hidden className="flex size-14 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-2xl">
                    {getCategoryIcon(place.category)}
                  </div>
                  <div className="flex flex-col justify-center">
                    <p className="font-semibold">{place.name}</p>
                    <p className="text-xs text-neutral-500">
                      {getCategoryLabel(place.category)} · {formatDistance(place.distance)} · 도보 {walkMinutes(place.distance)}분
                    </p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
