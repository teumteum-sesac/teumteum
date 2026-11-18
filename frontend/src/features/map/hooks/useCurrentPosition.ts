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
}

const GEO_OPTIONS: PositionOptions = { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 }

const hasGeolocation = () => typeof navigator !== 'undefined' && 'geolocation' in navigator

/** 위치 권한 상태. 브라우저가 permissions API 를 지원하지 않으면 null */
async function queryPermission(): Promise<PermissionStatus | null> {
  try {
    return (await navigator.permissions?.query({ name: 'geolocation' })) ?? null
  } catch {
    return null
  }
}

/** 현재 위치. 권한 거부·실패 시 서울시청을 기본 위치로 돌려준다. (화면설계서 HOM-01) */
export function useCurrentPosition({ watchPermission = false }: Options = {}): CurrentPosition {
  const [state, setState] = useState<PositionState>(() =>
    hasGeolocation()
      ? { position: DEFAULT_POSITION, denied: false, loading: true, fixId: 0 }
      : { position: DEFAULT_POSITION, denied: true, loading: false, fixId: 0 },
  )
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
          setState((s) => ({
            position: { lat: pos.coords.latitude, lng: pos.coords.longitude },
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

  // 처음 들어왔을 때 한 번 위치를 받는다 (권한을 아직 안 정했으면 브라우저 권한 팝업)
  useEffect(() => {
    void locate()
  }, [locate])

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
