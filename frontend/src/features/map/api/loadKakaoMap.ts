import type { KakaoMapsApi } from '../types'

let loading: Promise<KakaoMapsApi> | null = null

/**
 * 카카오 지도 SDK를 한 번만 불러온다. 지도를 쓰는 곳은 모두 이 함수를 거친다.
 * 카카오 개발자 콘솔 > 앱 > 플랫폼(Web)에 http://localhost:5173 이 등록되어 있어야 한다.
 */
export function loadKakaoMap(): Promise<KakaoMapsApi> {
  if (loading) return loading

  const appKey = import.meta.env.VITE_KAKAO_MAP_APP_KEY
  if (!appKey) {
    return Promise.reject(new Error('카카오 지도 키가 없습니다. frontend/.env.local 의 VITE_KAKAO_MAP_APP_KEY 를 확인하세요.'))
  }

  loading = new Promise<KakaoMapsApi>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services`
    script.async = true
    script.onload = () => {
      const maps = window.kakao?.maps
      if (!maps) {
        // 실패 상태를 남기지 않아야 다음 호출(다른 지도 화면 진입 등)에서 처음부터 다시 불러온다
        loading = null
        script.remove()
        reject(new Error('카카오 지도 SDK를 초기화하지 못했습니다.'))
        return
      }
      maps.load(() => resolve(maps))
    }
    script.onerror = () => {
      loading = null
      // 실패한 <script> 태그가 쌓이지 않게 지운다 (이미 실행된 SDK 코드나 window.kakao 는 되돌려지지 않음)
      script.remove()
      reject(new Error('카카오 지도 SDK를 불러오지 못했습니다. 앱 키와 등록된 도메인을 확인하세요.'))
    }
    document.head.appendChild(script)
  })

  return loading
}
