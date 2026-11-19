import { useCallback, useRef, useState } from 'react'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import type { LatLng } from '../../../shared/types/domain'
// 장소·주소 검색 API (GET /api/places/search, 정종일 #63·#99). 홈 검색창·위치 설정과 같은 함수를 그대로 쓴다 (API 는 수정하지 않음)
import { searchPlaces } from '../../condition/api/conditionApi'
import type { PlaceSearchResult } from '../../condition/types'

/** 주변 탐색 검색 결과 한 줄 (화면용). 반경 안팎은 탐색 기준 위치에서의 직선거리로 정한다 */
export interface NearbySearchItem {
  /** 목록·마커 구분값. 장소는 카카오 장소 ID, 주소 결과는 좌표로 만든다 */
  id: string
  /** 카카오 장소 ID. 주소 결과는 null (상세 보기 없음) */
  kakaoPlaceId: string | null
  type: PlaceSearchResult['type']
  name: string
  lat: number
  lng: number
  /** 도로명 주소 우선, 없으면 지번 */
  address: string | null
  categoryGroupCode: string | null
  /** 카카오 카테고리의 마지막 칸 (예: 분식) */
  categoryLabel: string | null
  /** 탐색 기준 위치에서의 직선거리(m) */
  distance: number
  /** 지금 고른 반경 안이면 true */
  inRadius: boolean
}

/** idle: 검색 안 함(주변 탐색) · loading · done · error */
export type NearbySearchState =
  | { status: 'idle' }
  | { status: 'loading'; query: string }
  | { status: 'done'; query: string; results: PlaceSearchResult[] }
  | { status: 'error'; query: string; message: string }

/**
 * 주변 탐색(HOM-02) 화면 안 검색. 검색 기준 좌표(탐색 기준 위치)를 함께 보내 거리를 받는다.
 * 검색 API 는 반경 제한 없이 정확도순으로 돌려주므로, 결과 순서는 그대로 두고 반경 안팎 판정은 화면에서 한다.
 * - 늦게 도착한 이전 검색 응답은 버린다 (마지막 검색어 결과만 남음)
 * - clear() 하면 검색 전 주변 탐색 상태로 돌아간다 (주변 장소는 다시 부르지 않음)
 */
export function useNearbySearch(center: LatLng) {
  const [state, setState] = useState<NearbySearchState>({ status: 'idle' })
  // 검색할 때마다 1씩 늘린다. 응답이 왔을 때 값이 다르면 이미 새 검색(또는 지우기)을 한 것이라 버린다
  const seqRef = useRef(0)
  const { lat, lng } = center

  const search = useCallback(
    async (query: string) => {
      const seq = ++seqRef.current
      setState({ status: 'loading', query })
      try {
        const results = await searchPlaces(query, { lat, lng })
        if (seqRef.current === seq) setState({ status: 'done', query, results })
      } catch (e) {
        if (seqRef.current === seq) setState({ status: 'error', query, message: e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE })
      }
    },
    [lat, lng],
  )

  const clear = useCallback(() => {
    seqRef.current++
    setState({ status: 'idle' })
  }, [])

  return { state, search, clear }
}
