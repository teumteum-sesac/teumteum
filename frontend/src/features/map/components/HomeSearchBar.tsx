import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import Icon from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import type { LatLng } from '../../../shared/types/domain'
// 장소·주소 검색 API 는 위치 설정(CON-02)과 같은 것을 쓴다 (정종일 #63·#99). 같은 함수를 두 벌 만들지 않으려고 그대로 가져온다
import { MAX_QUERY_LENGTH, searchPlaces, toLocationValue } from '../../condition/api/conditionApi'
import { useConditionStore } from '../../condition/store'
import type { PlaceSearchResult } from '../../condition/types'
import { formatDistance } from '../format'
import { KAKAO_GROUP_CATEGORY } from '../kakaoCategory'

/*
 * [이 파일이 하는 일]
 * 홈 검색창 (HOM-01 ①, REQ-052) + 주변 탐색 검색창 (HOM-02). GET /api/places/search 로 장소·주소를 찾는다.
 *
 * 입력하는 동안 실시간 추천 목록을 띄운다 (자동완성):
 *   글자를 치고 입력이 0.3초 멈추면 검색해서 검색창 바로 아래에 목록을 보여 준다. 엔터를 누를 필요가 없다.
 *   (위치 설정 CON-02 의 자동완성과 같은 간격. 한 글자 칠 때마다 부르면 카카오 API 호출이 너무 많아진다)
 *
 * 목록에서 고르면
 *   - 홈: 장소(PLACE) → 장소 미리보기(/places/{카카오 장소 ID}), 주소(ADDRESS) → 그 위치를 출발지로 외출 조건(CON-01)으로
 *   - 주변 탐색: onPickSuggestion 으로 바깥(HomeScreen)에 알린다 → 시트 검색 결과에서 그 장소를 고르고 지도를 옮긴다
 * 엔터를 누르면
 *   - 홈: 기다리지 않고 바로 검색 (목록은 그대로 검색창 아래)
 *   - 주변 탐색: onSubmitQuery 로 바깥에 넘긴다 → 결과 전체가 바텀시트에 뜬다. 추천 목록은 닫는다
 * center(현재 위치·탐색 기준)를 주면 결과마다 거리가 함께 온다.
 */

/** 입력이 멈추고 이만큼(ms) 뒤 검색 (CON-02 와 같은 값) */
const SEARCH_DELAY_MS = 300

type SearchState =
  | { status: 'idle' }
  | { status: 'loading'; query: string }
  | { status: 'done'; query: string; results: PlaceSearchResult[] }
  | { status: 'error'; query: string; message: string }

interface HomeSearchBarProps {
  /** 거리 계산 기준 (홈: 현재 위치, 주변 탐색: 탐색 기준 위치) */
  center?: LatLng | null
  /**
   * 주변 탐색(HOM-02) 검색 모드: 엔터로 제출한 검색어를 바깥에 맡긴다 (결과는 주변 탐색 바텀시트에 표시).
   * 이 값을 주면 목록에서 골라도 다른 화면으로 이동하지 않는다
   */
  onSubmitQuery?: (query: string) => void
  /** 주변 탐색 검색 모드: 추천 목록에서 하나를 골랐을 때 (검색어와 고른 결과) */
  onPickSuggestion?: (query: string, result: PlaceSearchResult) => void
  /** 주변 탐색 검색 모드: 검색어를 지웠을 때 (검색 전 주변 탐색으로 돌아감) */
  onClearQuery?: () => void
}

export default function HomeSearchBar({ center, onSubmitQuery, onPickSuggestion, onClearQuery }: HomeSearchBarProps) {
  const navigate = useNavigate()
  const setOrigin = useConditionStore((s) => s.setOrigin)
  const [keyword, setKeyword] = useState('')
  const [state, setState] = useState<SearchState>({ status: 'idle' })
  // 추천 목록을 보여 줄지. 입력·검색창 누르기 → 열림, 바깥 누르기·주변 탐색 엔터·고르기 → 닫힘
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  // 검색창 + 목록을 감싼 영역. 이 밖을 누르면 목록을 닫는다
  const containerRef = useRef<HTMLDivElement>(null)
  // 마지막으로 검색을 요청한 검색어. 엔터로 이미 검색한 검색어를 0.3초 뒤 자동 검색이 한 번 더 부르지 않게 한다
  const requestedRef = useRef('')
  const nearbyMode = onSubmitQuery !== undefined
  // 객체(center)를 그대로 의존값에 넣으면 렌더마다 새 객체라 검색이 다시 돌 수 있어 숫자로 꺼내 둔다
  const centerLat = center?.lat
  const centerLng = center?.lng

  const search = async (query: string) => {
    requestedRef.current = query
    setState({ status: 'loading', query })
    try {
      const results = await searchPlaces(query, centerLat !== undefined && centerLng !== undefined ? { lat: centerLat, lng: centerLng } : null)
      // 늦게 온 이전 검색 응답이 새 검색 결과를 덮지 않게, 지금 검색어와 같을 때만 반영한다
      setState((prev) => (prev.status === 'loading' && prev.query === query ? { status: 'done', query, results } : prev))
    } catch (e) {
      const message = e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE
      setState((prev) => (prev.status === 'loading' && prev.query === query ? { status: 'error', query, message } : prev))
    }
  }

  // 실시간 추천: 입력이 0.3초 멈추면 검색한다. 그 사이 또 입력하면 앞의 예약은 취소된다 (cleanup 의 clearTimeout)
  useEffect(() => {
    const query = keyword.trim()
    if (!query || !open) return
    const timer = setTimeout(() => {
      // 엔터로 이미 같은 검색어를 검색했으면 다시 부르지 않는다
      if (requestedRef.current === query) return
      void search(query)
    }, SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
    // search 는 렌더마다 새로 만들어지는 함수라 넣지 않는다 (넣으면 매 렌더마다 타이머가 다시 잡힘). 쓰는 값(center)은 숫자로 넣었다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, open, centerLat, centerLng])

  // 목록이 열려 있을 때 바깥(지도 등)을 누르면 닫는다. 검색창에서 포커스가 빠지는 것(blur)으로 닫으면
  // 휴대폰 키보드만 내려도 목록이 사라지고, 목록 항목을 누르는 순간에도 먼저 닫혀 버려 쓰지 않는다
  useEffect(() => {
    if (!open) return
    const closeOnOutside = (e: PointerEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', closeOnOutside)
    return () => document.removeEventListener('pointerdown', closeOnOutside)
  }, [open])

  const clearKeyword = () => {
    setKeyword('')
    setState({ status: 'idle' })
    requestedRef.current = ''
    onClearQuery?.()
    inputRef.current?.focus()
  }

  /** 홈 · 주소 결과: 그 위치에서 출발하는 외출 조건으로 */
  const startFrom = (result: PlaceSearchResult) => {
    setOrigin(toLocationValue(result))
    navigate('/condition')
  }

  /** 주변 탐색 · 목록에서 고르기: 시트·지도 쪽에 맡기고 목록과 키보드를 닫는다 */
  const pick = (result: PlaceSearchResult) => {
    const query = state.status === 'idle' ? keyword.trim() : state.query
    onPickSuggestion?.(query, result)
    setOpen(false)
    inputRef.current?.blur()
  }

  // 검색어가 있고, 목록이 열려 있고, 검색을 한 번이라도 시작했을 때만 목록을 그린다
  const showList = open && keyword.trim().length > 0 && state.status !== 'idle'

  return (
    <div ref={containerRef} className="flex flex-col gap-2">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          const query = keyword.trim()
          if (!query) return
          if (onSubmitQuery) {
            // 주변 탐색: 결과 전체는 바텀시트에. 추천 목록이 시트 위를 가리지 않게 닫고, 휴대폰 키보드도 내린다
            onSubmitQuery(query)
            setOpen(false)
            inputRef.current?.blur()
          } else {
            // 홈: 0.3초를 기다리지 않고 바로 검색
            setOpen(true)
            void search(query)
          }
        }}
        className="flex h-12 items-center gap-2 rounded-2xl border-2 border-ink bg-cream px-3.5 shadow-[0_3px_0_var(--color-ink)]"
      >
        <Icon name="search" className="size-5 text-ink" />
        <input
          ref={inputRef}
          type="search"
          enterKeyHint="search"
          value={keyword}
          maxLength={MAX_QUERY_LENGTH}
          // 자동완성 목록과 연결 (화면 읽기 프로그램이 "목록이 펼쳐짐"을 알 수 있게)
          role="combobox"
          aria-expanded={showList}
          aria-controls="home-search-suggestions"
          aria-autocomplete="list"
          autoComplete="off"
          onFocus={() => setOpen(true)}
          onChange={(e) => {
            setKeyword(e.target.value)
            setOpen(true)
            if (!e.target.value.trim()) {
              setState({ status: 'idle' })
              requestedRef.current = ''
              if (!e.target.value) onClearQuery?.()
            }
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setOpen(false)
          }}
          placeholder="장소, 주소 검색"
          aria-label="장소, 주소 검색"
          className="h-full min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-400 [&::-webkit-search-cancel-button]:appearance-none"
        />
        {/* 검색어 지우기 (홈·주변 탐색 공통). 주변 탐색은 검색 전 주변 탐색으로 돌아간다 */}
        {keyword && (
          <button
            type="button"
            onClick={clearKeyword}
            aria-label="검색어 지우기"
            className="flex size-8 shrink-0 items-center justify-center rounded-full text-neutral-500 active:bg-neutral-100"
          >
            <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M4 4l8 8M12 4l-8 8" />
            </svg>
          </button>
        )}
      </form>

      {showList && (
        <div id="home-search-suggestions" className="paper-card max-h-72 overflow-y-auto" aria-busy={state.status === 'loading'}>
          {state.status === 'loading' && (
            <p role="status" className="p-4 text-sm text-neutral-500">
              ‘{state.query}’ 찾는 중…
            </p>
          )}
          {state.status === 'error' && (
            <div role="alert" className="flex items-center justify-between gap-2 p-4 text-sm text-red-600">
              {state.message}
              <button type="button" onClick={() => void search(state.query)} className="shrink-0 font-bold underline underline-offset-2">
                다시 검색
              </button>
            </div>
          )}
          {state.status === 'done' && state.results.length === 0 && (
            <p className="p-4 text-sm text-neutral-500">‘{state.query}’ 검색 결과가 없어요.</p>
          )}
          {state.status === 'done' && state.results.length > 0 && (
            <ul>
              {state.results.map((result) => (
                <li
                  key={result.kakaoPlaceId ?? `${result.name}-${result.latitude}-${result.longitude}`}
                  className="border-b border-dashed border-line last:border-0"
                >
                  {nearbyMode ? (
                    // 주변 탐색: 화면을 옮기지 않고 시트·지도에서 그 장소를 고른다
                    <button type="button" onClick={() => pick(result)} className="flex w-full items-center gap-3 px-3 py-2.5 text-left">
                      <ResultRow result={result} nearbyMode />
                    </button>
                  ) : result.type === 'PLACE' && result.kakaoPlaceId ? (
                    <Link
                      to={`/places/${result.kakaoPlaceId}`}
                      state={{
                        place: {
                          kakaoPlaceId: result.kakaoPlaceId,
                          name: result.name,
                          category: result.categoryGroupCode ? (KAKAO_GROUP_CATEGORY[result.categoryGroupCode] ?? null) : null,
                          subCategory: result.categoryName?.split(' > ').pop() ?? null,
                          address: result.roadAddress ?? result.address,
                          latitude: result.latitude,
                          longitude: result.longitude,
                          distance: result.distance,
                        },
                      }}
                      className="flex items-center gap-3 px-3 py-2.5"
                    >
                      <ResultRow result={result} />
                    </Link>
                  ) : (
                    <button type="button" onClick={() => startFrom(result)} className="flex w-full items-center gap-3 px-3 py-2.5 text-left">
                      <ResultRow result={result} />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

/** 결과 한 줄: 카테고리 스티커(없으면 핀) + 이름 + 카테고리·거리·주소 */
function ResultRow({ result, nearbyMode = false }: { result: PlaceSearchResult; nearbyMode?: boolean }) {
  const category = result.categoryGroupCode ? KAKAO_GROUP_CATEGORY[result.categoryGroupCode] : undefined
  const address = result.roadAddress ?? result.address
  // 카카오 카테고리 이름은 "음식점 > 한식 > 국밥" 처럼 길어서 마지막 칸만 보여 준다
  // 주소 결과: 홈에서는 누르면 그 위치에서 출발하므로 '여기서 출발'을 붙이고, 주변 탐색에서는 지도만 옮기므로 '주소'만
  const categoryLabel = result.type === 'ADDRESS' ? (nearbyMode ? '주소' : '주소 · 여기서 출발') : result.categoryName?.split(' > ').pop()
  const meta = [categoryLabel, result.distance !== null ? formatDistance(result.distance) : null, address !== result.name ? address : null]
    .filter(Boolean)
    .join(' · ')

  return (
    <>
      {category ? (
        <StickerImage name={CATEGORY_STICKER[category]} className="size-9" />
      ) : (
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary-dark">
          <Icon name={result.type === 'ADDRESS' ? 'flag' : 'location'} className="size-5" />
        </span>
      )}
      <span className="min-w-0 flex-1">
        <p className="truncate text-sm font-bold">{result.name}</p>
        {meta && <p className="truncate text-xs text-neutral-500">{meta}</p>}
      </span>
      <Icon name="chevronRight" className="size-4 text-neutral-400" />
    </>
  )
}
