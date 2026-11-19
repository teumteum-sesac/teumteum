import { useEffect, useRef, useState } from 'react'
import { DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import type { LatLng } from '../../../shared/types/domain'
import { getNearbyPlaces } from '../api/nearbyApi'
import type { NearbyPlaceResult } from '../types'

/** idle: 부르지 않음(위치 확인 중·검색 중) · loading: 불러오는 중 · success: 받음 · error: 실패 */
export type NearbyPlacesStatus = 'idle' | 'loading' | 'success' | 'error'

/** 받은 결과를 다시 쓸 수 있는 시간(ms). 지나면 캐시를 보여 주면서 뒤에서 새로 부른다 */
const CACHE_TTL_MS = 5 * 60 * 1000
/** 캐시에 남길 조건(기준 좌표·반경) 수. 반경 3개 × 기준 좌표 2개 정도면 충분하다 */
const CACHE_LIMIT = 6

/** 한 조건(기준 좌표·반경)의 성공한 결과 */
interface CacheEntry {
  places: NearbyPlaceResult[]
  /** 받은 시각 (ms) */
  at: number
}

/** 마지막 실패. requestKey 로 어떤 요청(조건·다시 시도 횟수)의 실패인지 구분한다 */
interface FailedResult {
  requestKey: string
  message: string
}

/**
 * 주변 장소 목록을 불러와 상태로 들고 있는다 (HOM-02).
 * - enabled 가 true 일 때만 부른다 (현재 위치를 받은 뒤, 검색 중이 아닐 때). 검색 중에는 반경을 바꿔도 부르지 않는다
 * - 성공한 결과는 기준 좌표·반경별로 캐시한다. 같은 조건으로 돌아오면(검색어 삭제, 반경 되돌리기) 다시 부르지 않고 바로 쓴다
 * - 반경을 바꿔 새로 부르는 동안에는 이전 목록을 그대로 둬서 지도 마커가 깜빡이지 않게 한다.
 *   다만 검색 등으로 쉬었다가(enabled false → true) 캐시 없는 조건으로 돌아오면 이전 목록 대신 자리표시(로딩)를 보여 준다
 * - 늦게 도착한 이전 요청의 응답은 화면을 덮지 않는다 (캐시에만 넣는다)
 */
export function useNearbyPlaces(position: LatLng, radius: number, enabled: boolean) {
  const [cache, setCache] = useState<ReadonlyMap<string, CacheEntry>>(() => new Map())
  const [failed, setFailed] = useState<FailedResult | null>(null)
  // false 면 이전 목록을 이어서 보여 주지 않는다 (검색에서 돌아온 직후 → 자리표시)
  const [keepPrevious, setKeepPrevious] = useState(true)
  // '다시 시도'를 누를 때마다 1씩 늘려 다시 부른다
  const [retryCount, setRetryCount] = useState(0)
  const { lat, lng } = position
  // 캐시 키 (기준 좌표·반경) · 요청 키 (+ 다시 시도 횟수)
  const baseKey = `${lat},${lng},${radius}`
  const requestKey = `${baseKey},${retryCount}`

  // 쉬었다가 다시 부르기 시작하면 이전 목록을 이어서 보여 주지 않는다 (렌더 중 이전 값과 비교해 바로 반영)
  const [prevEnabled, setPrevEnabled] = useState(enabled)
  if (prevEnabled !== enabled) {
    setPrevEnabled(enabled)
    if (enabled) setKeepPrevious(false)
  }

  // 진행 중인 요청. 같은 요청(requestKey)이 또 오면 새로 부르지 않고 이 응답을 같이 쓴다
  // (개발 모드 StrictMode 가 effect 를 두 번 돌려 같은 요청이 동시에 두 번 나가고, 늦은 쪽을 기다리던 문제 방지)
  const inflightRef = useRef<{ key: string; promise: Promise<NearbyPlaceResult[]> } | null>(null)

  const cached = cache.get(baseKey)

  useEffect(() => {
    if (!enabled) return
    // 이 요청이 아직 최신인지. 조건이 바뀌거나 검색을 시작해 effect 가 정리(cleanup)되면 false 가 된다
    let latest = true
    // 캐시가 있으면 바로 보여 주고(아래 렌더 계산), 오래됐을 때만 뒤에서 새로 부른다
    if (cached && Date.now() - cached.at < CACHE_TTL_MS) return
    let inflight = inflightRef.current
    if (!inflight || inflight.key !== requestKey) {
      const promise = getNearbyPlaces({ lat, lng, radius })
      inflight = { key: requestKey, promise }
      inflightRef.current = inflight
      const key = baseKey
      // 화면이 이미 다른 조건으로 넘어갔어도, 받은 결과는 캐시에 넣어 둔다 (다시 돌아오면 바로 쓰도록)
      promise.then(
        (places) => {
          setCache((prev) => {
            const next = new Map(prev)
            next.delete(key)
            next.set(key, { places, at: Date.now() })
            // 오래된 것부터 지운다 (Map 은 넣은 순서를 기억한다)
            while (next.size > CACHE_LIMIT) next.delete(next.keys().next().value as string)
            return next
          })
        },
        () => {},
      )
      // 끝나면 비워서, 같은 조건으로 '다시 시도'하면 새로 부르게 한다
      const done = () => {
        if (inflightRef.current?.promise === promise) inflightRef.current = null
      }
      promise.then(done, done)
    }
    inflight.promise
      .then(() => {
        // 목록은 캐시(위)에서 읽는다. 여기서는 이전 목록 이어 보이기를 다시 켜고 실패 표시를 지운다
        if (!latest) return
        setKeepPrevious(true)
        setFailed(null)
      })
      .catch((e: unknown) => {
        // apiGet 은 모든 실패(서버 오류·서버 꺼짐·시간 초과)를 ApiError(message 포함)로 던진다
        if (latest) setFailed({ requestKey, message: e instanceof Error ? e.message : DEFAULT_ERROR_MESSAGE })
      })
    return () => {
      latest = false
    }
  }, [enabled, lat, lng, radius, baseKey, requestKey, cached])

  // 상태는 저장하지 않고 계산한다
  const failedNow = failed?.requestKey === requestKey ? failed : null
  let status: NearbyPlacesStatus = 'idle'
  if (enabled) status = failedNow ? 'error' : cached ? 'success' : 'loading'

  // 새 조건을 불러오는 동안 이어서 보여 줄 이전 목록 = 마지막으로 받은 결과 (캐시는 받은 순서대로 쌓인다)
  let previous: NearbyPlaceResult[] = []
  for (const entry of cache.values()) previous = entry.places
  let places: NearbyPlaceResult[] = []
  if (!failedNow) places = cached ? cached.places : keepPrevious ? previous : []

  return {
    places,
    status,
    errorMessage: failedNow?.message ?? null,
    retry: () => setRetryCount((n) => n + 1),
  }
}
