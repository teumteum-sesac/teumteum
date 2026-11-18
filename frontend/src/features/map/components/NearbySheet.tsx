import { useEffect, useRef, useState, type MouseEvent, type PointerEvent, type ReactNode } from 'react'
import Button from '../../../shared/components/Button'
import Chip from '../../../shared/components/Chip'
import ChipGroup from '../../../shared/components/ChipGroup'
import EmptyState from '../../../shared/components/EmptyState'
import { CATEGORIES, getCategoryIcon, getCategoryLabel, type CategoryCode } from '../../../shared/types/domain'
import { formatDistance, formatDistanceForRadius, walkMinutes } from '../format'
import type { NearbyPlacesStatus } from '../hooks/useNearbyPlaces'
import { clampSheetHeight, SHEET_HEIGHT_CLASS, snapSheetLevel, tapSheetLevel, type SheetLevel } from '../sheetLayout'
import type { NearbySearchItem } from '../hooks/useNearbySearch'
import type { NearbyPlaceResult } from '../types'
import { KAKAO_GROUP_CATEGORY } from '../kakaoCategory'
import PlaceCard, { PlaceCardSkeletonList } from './PlaceCard'

/** 주변 탐색 검색 모드에서 시트에 보여 줄 것 (HomeScreen 이 검색 상태로 만든다) */
export interface NearbySearchView {
  query: string
  status: 'loading' | 'done' | 'error'
  errorMessage: string | null
  /** 검색 API 순서(정확도순) 그대로. 반경 안팎은 inRadius */
  items: NearbySearchItem[]
  selectedId: string | null
  onSelect: (item: NearbySearchItem) => void
  onRetry: () => void
}

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
  /** 대분류로 거른 장소 목록 (HomeScreen 에서 계산). 서버가 반경 안의 장소를 가까운 순으로 보낸다 */
  places: NearbyPlaceResult[]
  /** 주변 장소 불러오기 상태 (useNearbyPlaces) */
  status: NearbyPlacesStatus
  /** 불러오기에 실패했을 때 보여 줄 문구 */
  errorMessage: string | null
  /** '다시 시도' 버튼 */
  onRetry: () => void
  /** 지도 마커나 카드로 고른 장소. 해당 카드를 강조하고 목록에서 보이게 스크롤한다 */
  selectedPlaceId: string | null
  /** 카드를 눌렀을 때: 지도 이동 + 마커 강조 (상세 이동은 선택된 카드의 '상세 보기') */
  onSelectPlace: (place: NearbyPlaceResult) => void
  /** 마커를 누를 때마다 바뀌는 값. 같은 마커를 다시 눌러도 해당 카드로 다시 스크롤한다 */
  scrollRequest: number
  /** true면 대분류와 상관없이 반경 안에 장소가 하나도 없다 (빈 목록 안내 문구 구분용) */
  radiusEmpty: boolean
  /** 시트 높이 단계. 지도 영역도 같은 값으로 줄이도록 HomeScreen 이 관리한다 */
  level: SheetLevel
  onLevelChange: (level: SheetLevel) => void
  /** 화면 안 검색 중이면 검색 결과를 보여 준다 (카테고리 칩은 숨기고 반경 선택은 그대로). null 이면 주변 장소 목록 */
  search?: NearbySearchView | null
}

/**
 * 주변 장소 탐색 바텀시트 (HOM-02): 접힘 / 반만(기본) / 전체 3단계.
 * - 손잡이·'내 주변 장소' 제목 줄을 누르면 반만 ↔ 접힘 (전체에서는 반만)
 * - 끌면 시트가 손가락을 따라 움직이고, 놓으면 가까운 단계로 정착한다 (길게 끌면 두 단계도 한 번에)
 * 끝까지 내려도 홈으로 가지 않고 접힘 상태로 주변 탐색을 유지한다.
 * 장소 카드를 누르면 지도를 그 장소로 옮기고 마커를 강조한다. 선택된 카드의 '상세 보기'로 장소 1곳짜리 상세(DET-01)에 간다.
 * 마커를 누르면 해당 카드를 강조한다.
 * 장소 데이터는 HomeScreen 이 주변 장소 API(useNearbyPlaces)로 받아 넘긴다. 불러오는 중·실패·결과 없음은 목록 자리에 안내한다
 */
export default function NearbySheet({
  radius,
  onRadiusChange,
  category,
  onCategoryChange,
  places,
  status,
  errorMessage,
  onRetry,
  selectedPlaceId,
  onSelectPlace,
  scrollRequest,
  radiusEmpty,
  level,
  onLevelChange,
  search = null,
}: NearbySheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null)
  // 목록 스크롤 영역 (주변 장소·검색 결과 공통). 고른 카드를 여기서 찾아 보이게 스크롤한다
  const listRef = useRef<HTMLDivElement>(null)
  // 강조·스크롤할 카드: 검색 중이면 검색 결과에서 고른 것, 아니면 주변 장소에서 고른 것
  const focusId = search ? search.selectedId : selectedPlaceId
  const drag = useRef<DragState | null>(null)
  // 끌기 중 시트 높이(px). null 이면 단계(level) 높이를 쓴다
  const [dragHeight, setDragHeight] = useState<number | null>(null)

  // 마커로 고른 카드가 목록 밖에 있으면 보이는 위치까지 스크롤한다.
  // 접힘에서 펼쳐지는 중이면 목록 높이가 아직 작으므로, 높이 애니메이션이 끝나면(transitionend) 한 번 더 맞춘다
  useEffect(() => {
    if (!focusId) return
    const sheet = sheetRef.current
    const scroll = () => {
      const card = listRef.current?.querySelector(`[data-place-id="${CSS.escape(focusId)}"]`)
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
  }, [focusId, scrollRequest])

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
          <h2 className="min-w-0 truncate font-bold">{search ? `‘${search.query}’ 검색 결과` : '내 주변 장소'}</h2>
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

      {/* 카테고리 칩은 검색 중에는 숨긴다 (검색 결과는 우리 8개 대분류로 정확히 나뉘지 않음). 검색어를 지우면 고르던 칩 그대로 */}
      {!search && (
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
      )}

      {/* 목록만 스크롤된다. 끝까지 스크롤해도 바깥(페이지)으로 넘기지 않는다. 시트 끌기는 위 손잡이·제목 줄에서만 */}
      <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-4">
        {search ? (
          <SearchResults search={search} radius={radius} />
        ) : (
          listNotice(status, errorMessage, onRetry, places.length, radiusEmpty) ?? (
          <>
            {/* 반경을 바꿔 다시 불러오는 동안에는 이전 목록을 그대로 두고 작게 알린다 */}
            {status === 'loading' && (
              <p role="status" className="pb-2 text-xs text-neutral-400">
                새로 불러오는 중…
              </p>
            )}
            <ul className="flex flex-col gap-2">
              {places.map((place) => (
                <PlaceCard
                  key={place.kakaoPlaceId}
                  id={place.kakaoPlaceId}
                  icon={getCategoryIcon(place.category)}
                  name={place.name}
                  meta={
                    <>
                      {getCategoryLabel(place.category)}
                      {/* 서버가 거리를 모르면(null) 거리·도보 시간은 빼고 대분류만 보여 준다 */}
                      {place.distance !== null && ` · ${formatDistance(place.distance)} · 도보 ${walkMinutes(place.distance)}분`}
                    </>
                  }
                  selected={place.kakaoPlaceId === selectedPlaceId}
                  onSelect={() => onSelectPlace(place)}
                  detailTo={`/results/place-${place.kakaoPlaceId}`}
                  detailState={{ name: place.name, lat: place.latitude, lng: place.longitude }}
                />
              ))}
            </ul>
          </>
          )
        )}
      </div>
    </div>
  )
}

/** 반경 안팎 표시 (검색 결과). 반경 밖도 고를 수 있으므로 흐리게 만들지 않고 라벨 색만 다르게 */
function RadiusBadge({ inRadius }: { inRadius: boolean }) {
  return inRadius ? (
    <span className="shrink-0 rounded-full bg-primary-light px-1.5 py-0.5 text-[10px] font-bold text-primary-dark">반경 내</span>
  ) : (
    <span className="shrink-0 rounded-full bg-neutral-100 px-1.5 py-0.5 text-[10px] font-bold text-neutral-500">반경 밖</span>
  )
}

/**
 * 검색 결과 목록: '반경 내' / '반경 밖' 두 묶음. 묶음 안에서는 검색 API 순서(정확도순)를 그대로 둔다.
 * 반경은 결과를 거르는 조건이 아니라 탐색 범위를 알려 주는 기준이므로, 반경 밖 장소도 같은 카드로 보여 주고 고를 수 있다
 */
function SearchResults({ search, radius }: { search: NearbySearchView; radius: number }) {
  if (search.status === 'loading') return <PlaceCardSkeletonList label={`‘${search.query}’ 찾는 중…`} count={3} />
  if (search.status === 'error') {
    return (
      <EmptyState
        message={search.errorMessage ?? '검색하지 못했어요.'}
        action={
          <Button variant="secondary" onClick={search.onRetry}>
            다시 검색
          </Button>
        }
      />
    )
  }
  if (search.items.length === 0) return <EmptyState message={`‘${search.query}’ 검색 결과가 없어요.`} />

  const groups = [
    { key: 'in', title: '반경 내', items: search.items.filter((i) => i.inRadius), dot: 'bg-primary' },
    { key: 'out', title: '반경 밖', items: search.items.filter((i) => !i.inRadius), dot: 'bg-neutral-300' },
  ].filter((g) => g.items.length > 0)

  return (
    <>
      <p className="pb-2 text-xs text-neutral-500">
        반경 {formatDistance(radius)} 기준 · 거리는 탐색 위치에서 잰 직선거리예요. 반경 밖 장소도 함께 보여요.
      </p>
      {groups.map((group) => (
        <section key={group.key} aria-label={`${group.title} ${group.items.length}곳`} className="pb-3">
          <h3 className="flex items-center gap-1.5 pb-2 text-sm font-bold">
            <span aria-hidden className={`size-2 rounded-full ${group.dot}`} />
            {group.title} <span className="font-normal text-neutral-500">{group.items.length}</span>
          </h3>
          <ul className="flex flex-col gap-2">
            {group.items.map((item) => {
              const category = item.categoryGroupCode ? KAKAO_GROUP_CATEGORY[item.categoryGroupCode] : undefined
              const kind = item.categoryLabel ?? (item.type === 'ADDRESS' ? '주소' : '장소')
              return (
                <PlaceCard
                  key={item.id}
                  id={item.id}
                  icon={category ? getCategoryIcon(category) : '📍'}
                  name={item.name}
                  badge={<RadiusBadge inRadius={item.inRadius} />}
                  meta={[kind, `직선 ${formatDistanceForRadius(item.distance)}`, item.address !== item.name ? item.address : null].filter(Boolean).join(' · ')}
                  selected={item.id === search.selectedId}
                  onSelect={() => search.onSelect(item)}
                  detailTo={item.kakaoPlaceId ? `/results/place-${item.kakaoPlaceId}` : null}
                  detailState={{ name: item.name, lat: item.lat, lng: item.lng }}
                />
              )
            })}
          </ul>
        </section>
      ))}
    </>
  )
}

/**
 * 목록 대신 보여 줄 안내. null 이면 목록을 그린다.
 * 실패 → 서버 문구 + 다시 시도 / 목록이 비었을 때: 위치 확인 중 · 처음 불러오는 중 · 결과 없음(반경 / 카테고리 구분)
 */
function listNotice(
  status: NearbyPlacesStatus,
  errorMessage: string | null,
  onRetry: () => void,
  count: number,
  radiusEmpty: boolean,
): ReactNode {
  if (status === 'error') {
    return (
      <EmptyState
        message={errorMessage ?? '주변 장소를 불러오지 못했어요.'}
        action={
          <Button variant="secondary" onClick={onRetry}>
            다시 시도
          </Button>
        }
      />
    )
  }
  if (count > 0) return null
  if (status === 'idle') return <EmptyState message="현재 위치를 확인하고 있어요." />
  if (status === 'loading') return <EmptyState message="주변 장소를 불러오고 있어요." />
  return (
    <EmptyState
      message={radiusEmpty ? '이 반경에 장소가 없어요. 반경을 넓혀보세요.' : '이 카테고리의 장소가 없어요. 다른 카테고리를 골라보세요.'}
    />
  )
}
