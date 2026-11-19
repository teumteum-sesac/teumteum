import { useCallback, useEffect, useRef, useState } from 'react'
import { DEFAULT_POSITION, type LatLng } from '../../../shared/types/domain'

interface PositionState {
  position: LatLng
  /** 위치 권한 거부·실패 여부. true면 position은 기본 위치(서울시청) */
  denied: boolean
  loading: boolean
  /** 위치를 새로 받을 때마다 1씩 늘어난다. 같은 좌표를 다시 받아도 지도를 옮길 수 있게 한다 */
  fixId: number
}

interface CurrentPosition extends PositionState {
  /**
   * 현재 위치를 다시 요청한다 (홈 내 위치 버튼·안내 카드 '다시 시도').
   * 요청 중에 또 부르면 새로 요청하지 않고 진행 중인 요청 결과를 같이 받는다. 성공하면 true
   */
  locate: () => Promise<boolean>
}

interface Options {
  /**
   * true면 위치 권한이 바뀌거나(설정에서 허용) 화면으로 돌아왔을 때 상태를 다시 판단한다 (홈 지도 컨트롤).
   * 꺼짐 상태에서 권한이 허용돼 있으면 위치를 다시 받는다
   */
  watchPermission?: boolean
  /**
   * 이 시간(ms) 안에 다른 화면(예: 홈)에서 받은 위치가 있으면 위치를 다시 묻지 않고 바로 쓴다 (주변 탐색 첫 로딩 단축).
   * 없거나 오래됐으면 평소처럼 새로 받는다
   */
  reuseRecentMs?: number
}

const GEO_OPTIONS: PositionOptions = { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 }

/** 이 앱에서 마지막으로 받은 현재 위치와 받은 시각. 화면을 옮겨도(홈 → 주변 탐색) 남아 있다. 새로고침하면 사라진다 */
let lastFix: { position: LatLng; at: number } | null = null

function rememberFix(position: LatLng) {
  lastFix = { position, at: Date.now() }
}

/** maxAgeMs 안에 받은 위치. 없으면 null */
function recentFix(maxAgeMs: number | undefined): LatLng | null {
  if (!maxAgeMs || !lastFix || Date.now() - lastFix.at > maxAgeMs) return null
  return lastFix.position
}

const hasGeolocation = () => typeof navigator !== 'undefined' && 'geolocation' in navigator

/** 위치 권한 상태. 브라우저가 permissions API 를 지원하지 않으면 null */
async function queryPermission(): Promise<PermissionStatus | null> {
  try {
    return (await navigator.permissions?.query({ name: 'geolocation' })) ?? null
  } catch {
    return null
  }
}

/**
 * 현재 위치 좌표만 한 번 받아 온다. 이 훅의 상태(position·denied)는 바꾸지 않는다.
 * 주변 탐색(HOM-02) 현재 위치 버튼처럼 지도만 옮기고 검색 기준 좌표는 그대로 둘 때 쓴다. 실패하면 null
 */
export function requestCurrentPosition(): Promise<LatLng | null> {
  if (!hasGeolocation()) return Promise.resolve(null)
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const position = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        rememberFix(position)
        resolve(position)
      },
      () => resolve(null),
      GEO_OPTIONS,
    )
  })
}

/** 현재 위치. 권한 거부·실패 시 서울시청을 기본 위치로 돌려준다. (화면설계서 HOM-01) */
export function useCurrentPosition({ watchPermission = false, reuseRecentMs }: Options = {}): CurrentPosition {
  // 처음 그릴 때 한 번만 정한다: 최근 위치를 다시 쓰면 위치 요청을 건너뛴다
  const [reused] = useState(() => recentFix(reuseRecentMs))
  const [state, setState] = useState<PositionState>(() => {
    if (reused) return { position: reused, denied: false, loading: false, fixId: 1 }
    return hasGeolocation()
      ? { position: DEFAULT_POSITION, denied: false, loading: true, fixId: 0 }
      : { position: DEFAULT_POSITION, denied: true, loading: false, fixId: 0 }
  })
  // 진행 중인 위치 요청. 있으면 새로 요청하지 않는다 (중복 탭 무시)
  const pendingRef = useRef<Promise<boolean> | null>(null)
  // 이벤트 콜백에서 지금 꺼짐 상태인지 읽기 위한 값
  const deniedRef = useRef(state.denied)
  useEffect(() => {
    deniedRef.current = state.denied
  })

  const locate = useCallback((): Promise<boolean> => {
    if (!hasGeolocation()) return Promise.resolve(false)
    if (pendingRef.current) return pendingRef.current
    const request = new Promise<boolean>((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const position = { lat: pos.coords.latitude, lng: pos.coords.longitude }
          rememberFix(position)
          setState((s) => ({
            position,
            denied: false,
            loading: false,
            fixId: s.fixId + 1,
          }))
          resolve(true)
        },
        () => {
          setState((s) => ({ ...s, position: DEFAULT_POSITION, denied: true, loading: false }))
          resolve(false)
        },
        GEO_OPTIONS,
      )
    }).finally(() => {
      pendingRef.current = null
    })
    pendingRef.current = request
    return request
  }, [])

  // 처음 들어왔을 때 한 번 위치를 받는다 (권한을 아직 안 정했으면 브라우저 권한 팝업). 최근 위치를 다시 쓰면 건너뛴다
  useEffect(() => {
    if (!reused) void locate()
  }, [locate, reused])

  // 권한 변경·화면 복귀 시 다시 판단
  useEffect(() => {
    if (!watchPermission || !hasGeolocation()) return
    let status: PermissionStatus | null = null
    let disposed = false
    const recheck = () => {
      if (!status) return
      if (status.state === 'denied') {
        setState((s) => (s.denied ? s : { ...s, position: DEFAULT_POSITION, denied: true, loading: false }))
      } else if (status.state === 'granted' && deniedRef.current) {
        void locate()
      }
    }
    const handleVisible = () => {
      if (document.visibilityState === 'visible') recheck()
    }
    void queryPermission().then((s) => {
      if (disposed || !s) return
      status = s
      status.addEventListener('change', recheck)
    })
    document.addEventListener('visibilitychange', handleVisible)
    return () => {
      disposed = true
      status?.removeEventListener('change', recheck)
      document.removeEventListener('visibilitychange', handleVisible)
    }
  }, [watchPermission, locate])

  return { ...state, locate }
}
