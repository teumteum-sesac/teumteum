import { useRef, useState } from 'react'
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
 * 홈 검색창 (HOM-01 ①, REQ-052). 검색어를 제출하면 GET /api/places/search 로 장소·주소를 찾아 아래에 목록으로 보여 준다.
 *   - 장소(PLACE) 결과 → 장소 1곳짜리 상세(DET-01, /results/place-{카카오 장소 ID}). 주변 탐색 카드와 같은 주소 규칙
 *   - 주소(ADDRESS) 결과 → 그 위치를 출발지로 정하고 외출 조건(CON-01)으로
 * center(현재 위치)를 주면 결과마다 거리가 함께 온다.
 */


type SearchState =
  | { status: 'idle' }
  | { status: 'loading'; query: string }
  | { status: 'done'; query: string; results: PlaceSearchResult[] }
  | { status: 'error'; query: string; message: string }

interface HomeSearchBarProps {
  /** 거리 계산 기준 (홈: 현재 위치) */
  center?: LatLng | null
  /**
   * 주변 탐색(HOM-02) 검색 모드: 검색어 제출을 바깥에 맡긴다. 주면 이 컴포넌트는 결과 목록을 그리지 않고
   * (결과는 주변 탐색 바텀시트에 표시) 다른 화면으로 이동하지도 않는다
   */
  onSubmitQuery?: (query: string) => void
  /** 주변 탐색 검색 모드: 검색어를 지웠을 때 (검색 전 주변 탐색으로 돌아감) */
  onClearQuery?: () => void
}

export default function HomeSearchBar({ center, onSubmitQuery, onClearQuery }: HomeSearchBarProps) {
  const navigate = useNavigate()
  const setOrigin = useConditionStore((s) => s.setOrigin)
  const [keyword, setKeyword] = useState('')
  const [state, setState] = useState<SearchState>({ status: 'idle' })
  const inputRef = useRef<HTMLInputElement>(null)
  const nearbyMode = onSubmitQuery !== undefined

  const clearKeyword = () => {
    setKeyword('')
    setState({ status: 'idle' })
    onClearQuery?.()
    inputRef.current?.focus()
  }

  const search = async (query: string) => {
    setState({ status: 'loading', query })
    try {
      const results = await searchPlaces(query, center)
      // 늦게 온 이전 검색 응답이 새 검색 결과를 덮지 않게, 지금 검색어와 같을 때만 반영한다
      setState((prev) => (prev.status === 'loading' && prev.query === query ? { status: 'done', query, results } : prev))
    } catch (e) {
      const message = e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE
      setState((prev) => (prev.status === 'loading' && prev.query === query ? { status: 'error', query, message } : prev))
    }
  }

  /** 주소 결과: 그 위치에서 출발하는 외출 조건으로 */
  const startFrom = (result: PlaceSearchResult) => {
    setOrigin(toLocationValue(result))
    navigate('/condition')
  }

  return (
    <div className="flex flex-col gap-2">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          const query = keyword.trim()
          if (!query) return
          if (onSubmitQuery) {
            onSubmitQuery(query)
            // 휴대폰 키보드를 닫아 바텀시트의 검색 결과가 가려지지 않게 한다
            inputRef.current?.blur()
          } else {
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
          onChange={(e) => {
            setKeyword(e.target.value)
            if (!e.target.value) {
              setState({ status: 'idle' })
              onClearQuery?.()
            }
          }}
          placeholder="장소, 주소 검색"
          aria-label="장소, 주소 검색"
          className="h-full min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-400 [&::-webkit-search-cancel-button]:appearance-none"
        />
        {/* 주변 탐색 검색 모드: 검색어 지우기 → 검색 전 주변 탐색으로 */}
        {nearbyMode && keyword && (
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

      {!nearbyMode && state.status !== 'idle' && (
        <div className="paper-card max-h-72 overflow-y-auto" aria-busy={state.status === 'loading'}>
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
                  {result.type === 'PLACE' && result.kakaoPlaceId ? (
                    <Link to={`/results/place-${result.kakaoPlaceId}`} className="flex items-center gap-3 px-3 py-2.5">
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
function ResultRow({ result }: { result: PlaceSearchResult }) {
  const category = result.categoryGroupCode ? KAKAO_GROUP_CATEGORY[result.categoryGroupCode] : undefined
  const address = result.roadAddress ?? result.address
  // 카카오 카테고리 이름은 "음식점 > 한식 > 국밥" 처럼 길어서 마지막 칸만 보여 준다
  const categoryLabel = result.type === 'ADDRESS' ? '주소 · 여기서 출발' : result.categoryName?.split(' > ').pop()
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
