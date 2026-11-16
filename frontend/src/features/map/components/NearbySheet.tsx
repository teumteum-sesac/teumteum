import { useEffect, useRef, type PointerEvent } from 'react'
import { Link } from 'react-router'
import Chip from '../../../shared/components/Chip'
import ChipGroup from '../../../shared/components/ChipGroup'
import EmptyState from '../../../shared/components/EmptyState'
import { CATEGORIES, getCategoryIcon, getCategoryLabel, type CategoryCode } from '../../../shared/types/domain'
import { formatDistance, walkMinutes, type NearbyPlace } from '../mock'
import { SHEET_HEIGHT_CLASS, SHEET_LEVELS, type SheetLevel } from '../sheetLayout'

const RADIUS_OPTIONS = [500, 1000, 3000]

/** 손잡이를 이만큼(px) 이상 끌면 단계를 바꾼다 */
const DRAG_THRESHOLD = 40

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
  /** true면 대분류와 상관없이 반경 안에 장소가 하나도 없다 (빈 목록 안내 문구 구분용) */
  radiusEmpty: boolean
  /** 시트 높이 단계. 지도 영역도 같은 값으로 줄이도록 HomeScreen 이 관리한다 */
  level: SheetLevel
  onLevelChange: (level: SheetLevel) => void
}

/**
 * 주변 장소 탐색 바텀시트 (HOM-02): 접힘 / 반만(기본) / 전체 3단계. 손잡이를 끌거나 눌러 바꾼다.
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
  radiusEmpty,
  level,
  onLevelChange,
}: NearbySheetProps) {
  const dragStartY = useRef<number | null>(null)
  const listRef = useRef<HTMLUListElement>(null)

  // 마커로 고른 카드가 목록 밖에 있으면 보이는 위치까지 스크롤한다
  useEffect(() => {
    if (!selectedPlaceId) return
    const card = listRef.current?.querySelector(`[data-place-id="${CSS.escape(selectedPlaceId)}"]`)
    card?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [selectedPlaceId])

  const onPointerDown = (e: PointerEvent<HTMLButtonElement>) => {
    dragStartY.current = e.clientY
    // 손잡이 밖으로 끌어도 pointerup 을 이 버튼이 받도록 잡아 둔다 (마우스 드래그 대응)
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onPointerUp = (e: PointerEvent<HTMLButtonElement>) => {
    if (dragStartY.current === null) return
    const dy = e.clientY - dragStartY.current
    dragStartY.current = null
    const index = SHEET_LEVELS.indexOf(level)
    if (Math.abs(dy) < DRAG_THRESHOLD) {
      // 짧게 누르면 한 단계 위로 (전체에서는 반만으로)
      onLevelChange(level === 'full' ? 'half' : SHEET_LEVELS[index + 1])
    } else if (dy < 0) {
      onLevelChange(SHEET_LEVELS[Math.min(index + 1, SHEET_LEVELS.length - 1)])
    } else {
      // 아래로 끌면 한 단계 아래로. 접힘이 가장 아래이고 홈으로는 이동하지 않는다
      onLevelChange(SHEET_LEVELS[Math.max(index - 1, 0)])
    }
  }
  const onPointerCancel = () => {
    dragStartY.current = null
  }

  return (
    <div
      className={`absolute inset-x-0 bottom-0 z-20 flex flex-col overflow-hidden rounded-t-2xl bg-white shadow-[0_-4px_16px_rgba(0,0,0,0.08)] transition-[height] ${SHEET_HEIGHT_CLASS[level]}`}
    >
      <button
        type="button"
        aria-label="시트 높이 조절 (끌어서 펼치기·접기)"
        onPointerDown={onPointerDown}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
        className="flex h-7 shrink-0 touch-none items-center justify-center"
      >
        <span className="h-1 w-10 rounded-full bg-neutral-300" />
      </button>

      <div className="flex items-center justify-between px-4 pb-2">
        <h2 className="font-bold">내 주변 장소</h2>
        <select
          value={radius}
          onChange={(e) => onRadiusChange(Number(e.target.value))}
          aria-label="반경"
          className="h-8 rounded-lg border border-line px-2 text-xs"
        >
          {RADIUS_OPTIONS.map((r) => (
            <option key={r} value={r}>
              반경 {formatDistance(r)}
            </option>
          ))}
        </select>
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

      {/* 목록만 스크롤된다. 끝까지 스크롤해도 바깥(페이지)으로 넘기지 않는다. 시트 드래그는 손잡이에서만 */}
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
