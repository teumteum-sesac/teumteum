import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { ApiError, DEFAULT_ERROR_MESSAGE } from "../../../shared/api/apiError";
import {getAddress, searchPlaces, toLocationValue} from "../api/conditionApi";
import Icon from '../../../shared/components/Icon'
import { DEFAULT_POSITION, type LocationValue } from '../../../shared/types/domain'
import MapCenterPicker from '../../map/components/MapCenterPicker'
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
 * 지도에서 직접 선택은 주소에 &map 을 붙여 지도 중앙 핀(MapCenterPicker) 화면으로 바꾼다.
 * 기록을 하나 쌓으므로 뒤로가기를 누르면 지도 → 이 목록 → CON-01 순서로 돌아간다
 */
export default function LocationSearch({ type }: LocationSearchProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const mapMode = searchParams.has('map')
  // 이 목록에서 지도를 열었는지. 그렇다면 지도 → 목록 → CON-01 두 단계를 돌아가야 한다 (주소로 바로 들어온 경우는 한 단계)
  const openedFromList = (location.state as { fromList?: boolean } | null)?.fromList === true
  const origin = useConditionStore((s) => s.origin)
  const destination = useConditionStore((s) => s.destination)
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
    navigate(mapMode && openedFromList ? -2 : -1)
  }

  const openMap = () => {
    const params = new URLSearchParams(searchParams)
    params.set('map', '')
    navigate({ search: params.toString() }, { state: { fromList: true } })
  }

  if (mapMode) {
    // 처음 지도 중심: 이미 정한 위치 → (도착지면) 출발지 → 서울시청
    const initialCenter = (type === 'origin' ? origin : (destination ?? origin)) ?? DEFAULT_POSITION
    return (
      // 화면 높이에서 상단 바(56px)를 뺀 만큼 지도를 채운다
      <MapCenterPicker initialCenter={initialCenter} onConfirm={(picked) => select(picked)} className="h-[calc(100dvh-3.5rem)]" />
    )
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
      <label className="flex h-12 items-center gap-2 rounded-2xl border-2 border-ink bg-cream px-3.5 shadow-[0_3px_0_var(--color-ink)]">
        <Icon name="search" className="size-5" />
        <input
          type="search"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="장소, 주소 검색"
          aria-label="장소, 주소 검색"
          autoFocus
          className="h-full min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-400"
        />
      </label>

      {keyword.trim() ? (
          <SearchResult search={search} keyword={keyword} onSelect={select} />
      ) : (
          <>
            <button
              type="button"
              onClick={selectCurrentLocation}
              disabled={locating}
              className="paper-card flex h-12 items-center gap-2 px-3.5 text-sm font-bold active:translate-y-0.5 disabled:text-neutral-400"
            >
              <span className="flex size-7 items-center justify-center rounded-full bg-primary-light text-primary-dark">
                <Icon name="location" className="size-4" />
              </span>
              {locating ? '현재 위치 확인 중…' : '현재 위치 사용'}
            </button>
          {message && <p className="text-sm text-red-500">{message}</p>}

          {type === 'destination' && (
            <button
              type="button"
              onClick={() => {
                setDestination(null)
                navigate(-1)
              }}
              className="px-1 text-left text-sm text-neutral-500 underline decoration-line decoration-2 underline-offset-4"
            >
              도착지 입력 안 함
            </button>
          )}

          <section>
            <h2 className="mb-2 px-1 font-display text-base">최근 검색</h2>
            {recent.length === 0 ? (
              <p className="px-1 text-sm text-neutral-400">최근 검색한 위치가 없습니다.</p>
            ) : (
              <LocationList locations={recent} onSelect={select} />
            )}
          </section>

          <button
            type="button"
            onClick={openMap}
            className="flex h-12 items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-line text-sm font-bold active:translate-y-0.5"
          >
            <Icon name="map" className="size-5" />
            지도에서 직접 선택
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
    <ul className="paper-card flex flex-col px-3.5">
      {locations.map((location) => (
        <li key={`${location.name}-${location.lat}-${location.lng}`} className="border-b border-dashed border-line last:border-0">
          <button type="button" onClick={() => onSelect(location)} className="flex w-full items-center gap-3 py-3 text-left">
            <Icon name="location" className="size-4 text-primary-dark" />
            <span className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold">{location.name}</p>
              {location.address && <p className="truncate text-xs text-neutral-500">{location.address}</p>}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}
