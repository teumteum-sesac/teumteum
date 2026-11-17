import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, DEFAULT_ERROR_MESSAGE } from "../../../shared/api/apiError";
import {getAddress, searchPlaces, toLocationValue} from "../api/conditionApi";
import type { LocationValue } from '../../../shared/types/domain'
import { useRecentLocationStore } from '../recentLocationStore'
import { useConditionStore } from '../store'

interface LocationSearchProps {
  type: 'origin' | 'destination'
}

/** 입력이 멈추고 이만큼(ms) 뒤 검색 */
const SEARCH_DELAY_MS = 300

/** 검색 상태: 입력 전(null) / 검색 중 / 결과 / 실패 */
type SearchState =
    | { status: 'loading'; query: string }
    | { status: 'done'; query: string; locations: LocationValue[] }
    | { status: 'error'; query: string; message: string }
    | null

function toErrorMessage(e: unknown): string {
  return e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE
}

/**
 * 위치 설정 (CON-02): 검색(자동완성) · 현재 위치 사용 · 최근 검색 최대 5개 · 지도에서 직접 선택.
 * 선택하면 장소명·주소·좌표를 저장하고 조건 설정(CON-01)으로 돌아간다.
 * 검색은 GET /api/places/search, 현재 위치 주소는 GET /api/places/address (REQ-007)
 *  * TODO(유지수): 지도 중앙 핀으로 위치 지정
 */
export default function LocationSearch({ type }: LocationSearchProps) {
  const navigate = useNavigate()
  const origin = useConditionStore((s) => s.origin)
  const setOrigin = useConditionStore((s) => s.setOrigin)
  const setDestination = useConditionStore((s) => s.setDestination)
  const { recent, add: addRecent } = useRecentLocationStore()
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState<SearchState>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [locating, setLocating] = useState(false)

  // 입력이 멈추고 0.3초 뒤 검색, 늦게 도착한 이전 검색 결과는 무시한다.
  useEffect(() => {
    const k = keyword.trim()
    if(!k) return
    let ignore = false
    const timer = setTimeout(async () => {
      setSearch({status: 'loading', query: k})
      try {
        const results = await searchPlaces(k, origin)
        if (!ignore) setSearch({ status: 'done', query: k, locations: results.map(toLocationValue) })
      } catch (e) {
        if (!ignore) setSearch({ status: 'error', query: k, message: toErrorMessage(e) })
      }
    }, SEARCH_DELAY_MS)
    return () => {
      ignore = true
      clearTimeout(timer)
    }
  }, [keyword, origin])


  const select = (location: LocationValue, remember = true) => {
    if (type === 'origin') setOrigin(location)
    else setDestination(location)
    if (remember) addRecent(location)
    navigate(-1)
  }

  const selectCurrentLocation = () => {
    if (!navigator.geolocation) {
      setMessage('이 브라우저는 위치 정보를 지원하지 않습니다.')
      return
    }
    setMessage(null)
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
        async (pos) => {
          const { latitude: lat, longitude: lng } = pos.coords
          // 주소를 못 찾거나 조회에 실패해도 좌표는 쓸 수 있으므로 "현재 위치"로 진행한다
          let name = '현재 위치'
          let address: string | undefined
          try {
            const result = await getAddress(lat, lng)
            name = result.displayName ?? name
            address = result.roadAddress ?? result.jibunAddress ?? undefined
          } catch {
            // 주소 표시만 못 할 뿐 위치 선택은 계속한다
          }
          setLocating(false)
          select({ name, address: address !== name ? address : undefined, lat, lng }, false)
        },
        () => {
          setLocating(false)
          setMessage('위치 권한이 거부되었습니다. 장소를 검색해 직접 설정해 주세요.')
        },
    )
  }

  return (
    <div className="flex flex-col gap-4 px-4 py-4">
      <input
        type="search"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        placeholder="🔍 장소, 주소 검색"
        aria-label="장소, 주소 검색"
        autoFocus
        className="h-12 rounded-xl border border-line px-4 text-sm outline-none focus:border-primary"
      />

      {keyword.trim() ? (
          <SearchResult search={search} keyword={keyword} onSelect={select} />
      ) : (
          <>
            <button
                type="button"
                onClick={selectCurrentLocation}
                disabled={locating}
                className="flex h-12 items-center gap-2 text-sm font-semibold text-primary disabled:text-neutral-400"
            >
              📍 {locating ? '현재 위치 확인 중…' : '현재 위치 사용'}
            </button>
          {message && <p className="text-sm text-red-500">{message}</p>}

          {type === 'destination' && (
            <button
              type="button"
              onClick={() => {
                setDestination(null)
                navigate(-1)
              }}
              className="text-left text-sm text-neutral-500"
            >
              도착지 입력 안 함
            </button>
          )}

          <section>
            <h2 className="mb-2 text-xs font-semibold text-neutral-400">최근 검색</h2>
            {recent.length === 0 ? (
              <p className="text-sm text-neutral-400">최근 검색한 위치가 없습니다.</p>
            ) : (
              <LocationList locations={recent} onSelect={select} />
            )}
          </section>

          <button type="button" disabled className="h-12 rounded-xl border border-line text-sm font-semibold disabled:text-neutral-400">
            🗺 지도에서 직접 선택 (준비 중)
          </button>
        </>
      )}
    </div>
  )
}

interface SearchResultProps {
  search: SearchState
  keyword: string
  onSelect: (location: LocationValue) => void
}

/* 검색어가 있을 때: 검색 중/실패/결과 없음/ 결과목록 */
function SearchResult({ search, keyword, onSelect}: SearchResultProps) {
  // 입력 직후(0.3초 대기 중)에도 검색 중으로 보여 준다.
  if (search === null || search.query !== keyword.trim() || search.status === 'loading') {
    return <p className={"text-sm text-neutral-400"}>검색 중...</p>
  }
  if (search.status === 'error') {
    return <p className="text-sm text-red-500">{search.message}</p>
  }
  if(search.locations.length === 0) {
    return <p className="text-sm text-neutral-400">‘{keyword.trim()}’ 검색 결과가 없어요.</p>
  }
  return <LocationList locations={search.locations} onSelect={onSelect} />
}

function LocationList({ locations, onSelect }: { locations: LocationValue[]; onSelect: (location: LocationValue) => void }) {
  return (
    <ul className="flex flex-col">
      {locations.map((location) => (
        <li key={`${location.name}-${location.lat}-${location.lng}`} className="border-b border-line last:border-0">
          <button type="button" onClick={() => onSelect(location)} className="w-full py-3 text-left">
            <p className="text-sm font-semibold">{location.name}</p>
            {location.address && <p className="text-xs text-neutral-500">{location.address}</p>}
          </button>
        </li>
      ))}
    </ul>
  )
}
