import { useEffect, useRef, useState, type MouseEvent, type PointerEvent, type ReactNode } from 'react'
import { PAPER_STYLE } from '../../../shared/assets/stickerAssets'
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
  /** 고른 대분류의 세분류 칩 목록 (반경 안에 있는 것만). 대분류가 전체면 빈 배열 */
  subCategories: string[]
  /** 고른 세분류 (null = 대분류 전체) */
  subCategory: string | null
  /** 세분류 칩을 눌렀을 때 (같은 칩을 다시 누르면 선택 해제) */
  onSubCategoryToggle: (name: string) => void
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
  subCategories,
  subCategory,
  onSubCategoryToggle,
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
    // 반경 버튼은 끌기·누르기 영역에서 뺀다
    if (e.target instanceof Element && e.target.closest('[data-no-drag]')) return
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
      // 배경: 코스 지도 패널과 같은 크림 종이 (흰 배경 대신 앱 톤에 맞춤)
      style={{ ...PAPER_STYLE, ...(dragHeight === null ? {} : { height: dragHeight }) }}
      className={`absolute inset-x-0 bottom-0 z-20 flex flex-col overflow-hidden rounded-t-[28px] border-2 border-b-0 border-line bg-cream shadow-[0_-4px_16px_rgba(0,0,0,0.08)] ${
        dragHeight === null ? 'transition-[height] duration-200' : ''
      } ${SHEET_HEIGHT_CLASS[level]}`}
    >
      {/* 끌기·누르기 영역: 손잡이 + '내 주변 장소' 제목 줄 (반경 버튼은 제외) */}
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
          {/* 접혀 있으면 오른쪽은 비워 둔다 (손잡이·제목 줄을 누르거나 끌면 펼쳐진다) */}
          {level === 'collapsed' ? null : (
            // 반경: 펼쳐 고르는 상자 대신 세 개를 늘어놓고 바로 누른다. 고른 것은 핑크 (이동수단 버튼과 같은 톤)
            <div data-no-drag role="radiogroup" aria-label="탐색 반경" className="flex shrink-0 gap-1">
              {RADIUS_OPTIONS.map((r) => {
                const selected = r === radius
                return (
                  <button
                    key={r}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    onClick={() => onRadiusChange(r)}
                    className={`h-8 cursor-pointer rounded-full border-2 px-2.5 text-xs ${
                      selected ? 'border-primary bg-primary-light font-bold text-ink' : 'border-line bg-white/70 text-neutral-600'
                    }`}
                  >
                    {r < 1000 ? `${r}m` : `${r / 1000}km`}
                  </button>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* 카테고리 칩은 검색 중에는 숨긴다 (검색 결과는 우리 8개 대분류로 정확히 나뉘지 않음). 검색어를 지우면 고르던 칩 그대로 */}
      {!search && (
        <div className="px-4 pb-2">
          <ChipGroup arrows>
            <Chip selected={category === null} onClick={() => onCategoryChange(null)}>
              전체
            </Chip>
            {CATEGORIES.map((c) => (
              <Chip key={c.code} selected={category === c.code} onClick={() => onCategoryChange(c.code)}>
                {c.label}
              </Chip>
            ))}
          </ChipGroup>
          {/* 세분류: 추천 결과(RES-01)와 같은 모양. 고른 대분류 아래 연한 핑크 받침 위에 작은 칩(level="sub") */}
          {category !== null && subCategories.length > 0 && (
            <div className="mt-2 rounded-2xl bg-primary-light/40 px-3 py-2">
              <ChipGroup>
                {subCategories.map((name) => (
                  <Chip key={name} level="sub" selected={subCategory === name} onClick={() => onSubCategoryToggle(name)}>
                    {name}
                  </Chip>
                ))}
              </ChipGroup>
            </div>
          )}
        </div>
      )}

      {/* 목록만 스크롤된다. 끝까지 스크롤해도 바깥(페이지)으로 넘기지 않는다. 시트 끌기는 위 손잡이·제목 줄에서만 */}
      <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-4">
        {search ? (
          <SearchResults search={search} radius={radius} />
        ) : (
          listNotice(status, errorMessage, onRetry, places.length, radiusEmpty) ?? (
          <>
            {/* 반경을 바꿔 다시 불러오는 동안에는 이전 목록을 그대로 둔다. 안내 띠는 화면에 그리지 않고 화면 읽기 프로그램에만 알린다 */}
            {status === 'loading' && (
              <p role="status" className="sr-only">
                반경 {formatDistance(radius)} 안의 장소를 새로 불러오는 중
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
                  // '상세 보기' → 장소 미리보기. 목록에서 받은 정보를 그대로 넘긴다 (#180 과 같은 형식)
                  detail={{
                    kakaoPlaceId: place.kakaoPlaceId,
                    name: place.name,
                    category: place.category,
                    subCategory: place.subCategory,
                    address: place.roadAddress ?? place.address,
                    latitude: place.latitude,
                    longitude: place.longitude,
                    distance: place.distance,
                  }}
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
                  // 장소(카카오 장소 ID 있음)만 미리보기로 갈 수 있다. 주소 결과는 상세 보기 없음
                  detail={
                    item.kakaoPlaceId
                      ? {
                          kakaoPlaceId: item.kakaoPlaceId,
                          name: item.name,
                          category: category ?? null,
                          subCategory: item.categoryLabel,
                          address: item.address,
                          latitude: item.lat,
                          longitude: item.lng,
                          distance: item.distance,
                        }
                      : null
                  }
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
  // 처음 불러올 때는 카드 모양 자리표시로 어디에 무엇이 나올지 보여 준다 (위치 확인 → 장소 불러오기 2단계 문구)
  if (status === 'idle') return <PlaceCardSkeletonList label="현재 위치를 확인하고 있어요…" />
  if (status === 'loading') return <PlaceCardSkeletonList label="주변 장소를 불러오고 있어요…" />
  return (
    <EmptyState
      message={radiusEmpty ? '이 반경에 장소가 없어요. 반경을 넓혀보세요.' : '이 카테고리의 장소가 없어요. 다른 카테고리를 골라보세요.'}
    />
  )
}
