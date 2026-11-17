import { useEffect, useState } from 'react'
import { DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import type { LatLng } from '../../../shared/types/domain'
import { getNearbyPlaces } from '../api/nearbyApi'
import type { NearbyPlaceResult } from '../types'

/** idle: 아직 부르지 않음(위치 확인 중) · loading: 불러오는 중 · success: 받음 · error: 실패 */
export type NearbyPlacesStatus = 'idle' | 'loading' | 'success' | 'error'

/** 서버에서 받은 마지막 결과. requestKey 로 어떤 요청(위치·반경·다시 시도 횟수)의 결과인지 구분한다 */
interface LoadedResult {
  requestKey: string
  places: NearbyPlaceResult[]
  /** 실패했으면 화면에 보여 줄 문구 (서버가 준 문장), 성공이면 null */
  errorMessage: string | null
}

/**
 * 주변 장소 목록을 불러와 상태로 들고 있는다 (HOM-02).
 * - enabled 가 true 일 때만 부른다 (현재 위치를 받은 뒤. 거부되면 서울시청 기준)
 * - 위치·반경이 바뀌면 다시 부른다. 다시 부르는 동안에는 이전 목록을 그대로 둬서 지도 마커가 깜빡이지 않게 한다
 * - 늦게 도착한 이전 요청의 응답은 버린다 (반경을 빠르게 바꿔도 마지막 반경 결과만 남는다)
 */
export function useNearbyPlaces(position: LatLng, radius: number, enabled: boolean) {
  const [loaded, setLoaded] = useState<LoadedResult | null>(null)
  // '다시 시도'를 누를 때마다 1씩 늘려 다시 부른다
  const [retryCount, setRetryCount] = useState(0)
  const { lat, lng } = position
  // 지금 화면이 원하는 요청. 받은 결과의 requestKey 와 다르면 아직 불러오는 중이다
  const requestKey = `${lat},${lng},${radius},${retryCount}`

  useEffect(() => {
    if (!enabled) return
    // 이 요청이 아직 최신인지. 위치·반경이 바뀌어 effect 가 정리(cleanup)되면 false 가 된다
    let latest = true
    getNearbyPlaces({ lat, lng, radius })
      .then((places) => {
        if (latest) setLoaded({ requestKey, places, errorMessage: null })
      })
      .catch((e: unknown) => {
        // apiGet 은 모든 실패(서버 오류·서버 꺼짐·시간 초과)를 ApiError(message 포함)로 던진다
        if (latest) setLoaded({ requestKey, places: [], errorMessage: e instanceof Error ? e.message : DEFAULT_ERROR_MESSAGE })
      })
    return () => {
      latest = false
    }
  }, [enabled, lat, lng, radius, requestKey])

  // 상태는 저장하지 않고 계산한다: 원하는 요청의 결과가 도착했는지로 판단
  const arrived = loaded?.requestKey === requestKey
  let status: NearbyPlacesStatus = 'idle'
  if (enabled) status = !arrived ? 'loading' : loaded.errorMessage ? 'error' : 'success'

  return {
    // 다시 불러오는 동안에도 이전에 성공한 목록을 보여 준다. 실패한 결과는 빈 목록
    places: loaded?.places ?? [],
    status,
    errorMessage: arrived ? loaded.errorMessage : null,
    retry: () => setRetryCount((n) => n + 1),
  }
}
