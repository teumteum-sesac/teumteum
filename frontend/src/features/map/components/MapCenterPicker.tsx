import { useEffect, useState } from 'react'
import Button from '../../../shared/components/Button'
import type { LatLng, LocationValue } from '../../../shared/types/domain'
import { getAddress } from '../api/addressApi'
import type { AddressResponse } from '../types'
import KakaoMap from './KakaoMap'

/** 지도가 멈춘 뒤 이만큼(ms) 기다렸다 주소를 조회한다 (지도를 계속 움직이는 동안 요청을 줄임) */
const ADDRESS_DELAY_MS = 300

/** 주소를 찾지 못했을 때 위치 이름 */
const FALLBACK_NAME = '선택한 위치'

interface MapCenterPickerProps {
  /** 처음 지도 중심 (예: 현재 위치, 이미 정한 출발지). 처음 그릴 때 한 번만 쓴다 */
  initialCenter: LatLng
  /** '이 위치로 설정'을 눌렀을 때. 핀이 가리키는 장소명·주소·좌표를 넘긴다 */
  onConfirm: (location: LocationValue) => void
  /** 확인 버튼 문구 */
  confirmLabel?: string
  className?: string
}

/** 마지막으로 받은 주소. requestKey 로 어느 좌표의 결과인지 구분한다 */
interface AddressResult {
  requestKey: string
  address: AddressResponse | null
  failed: boolean
}

/**
 * 지도 중앙 핀으로 위치 고르기 (CON-02 ④ 지도에서 직접 선택, REQ-007).
 * 지도를 끌어 핀 아래에 원하는 위치를 맞추면, 지도가 멈춘 위치의 주소를 보여 주고 확인 시 onConfirm 으로 넘긴다.
 * 핀은 지도 위에 겹쳐 둔 그림이라 지도 드래그·확대·축소를 막지 않는다.
 */
export default function MapCenterPicker({ initialCenter, onConfirm, confirmLabel = '이 위치로 설정', className = '' }: MapCenterPickerProps) {
  // 지도에 넘기는 처음 중심. 고정해 두고 바꾸지 않는다 (사용자가 움직인 위치는 지도가 들고 있다)
  const [mapCenter] = useState(initialCenter)
  // 핀이 가리키는 좌표. 지도 움직임이 끝날 때마다 KakaoMap 이 알려 준다
  const [center, setCenter] = useState<LatLng>(initialCenter)
  const [result, setResult] = useState<AddressResult | null>(null)

  const { lat, lng } = center
  const requestKey = `${lat},${lng}`

  // 핀 위치가 바뀌면 잠시 뒤 주소를 조회한다. 그 사이 다시 움직이면 이전 요청은 취소·무시한다
  useEffect(() => {
    let latest = true
    const timer = setTimeout(() => {
      getAddress({ lat, lng })
        .then((address) => {
          if (latest) setResult({ requestKey, address, failed: false })
        })
        .catch(() => {
          if (latest) setResult({ requestKey, address: null, failed: true })
        })
    }, ADDRESS_DELAY_MS)
    return () => {
      latest = false
      clearTimeout(timer)
    }
  }, [lat, lng, requestKey])

  // 지금 핀 위치의 주소가 도착했는지 (도착 전에는 '확인 중')
  const current = result?.requestKey === requestKey ? result : null
  const address = current?.address ?? null
  const subAddress = address?.roadAddress ?? address?.jibunAddress ?? null

  const confirm = () => {
    onConfirm({
      name: address?.displayName ?? FALLBACK_NAME,
      address: subAddress ?? undefined,
      lat,
      lng,
    })
  }

  return (
    <div className={`flex flex-col ${className}`}>
      <div className="relative min-h-0 flex-1">
        <KakaoMap center={mapCenter} level={3} onCenterChange={setCenter} className="size-full" />
        {/* 지도 중앙 고정 핀. pointer-events-none 이라 핀 위를 눌러도 지도를 끌고 확대할 수 있다. 핀 끝(아래쪽)이 지도 중심 */}
        <div aria-hidden className="pointer-events-none absolute top-1/2 left-1/2 z-10 -translate-x-1/2 -translate-y-full">
          <svg width="36" height="44" viewBox="0 0 36 44" className="text-primary drop-shadow">
            <path d="M18 43S4 27.7 4 18a14 14 0 0 1 28 0c0 9.7-14 25-14 25z" fill="currentColor" stroke="#fff" strokeWidth="2" />
            <circle cx="18" cy="18" r="5" fill="#fff" />
          </svg>
        </div>
        <div aria-hidden className="pointer-events-none absolute top-1/2 left-1/2 z-10 size-2 -translate-1/2 rounded-full bg-black/30" />
      </div>

      <div className="flex flex-col gap-3 border-t border-line bg-white px-4 py-4">
        <p className="text-xs text-neutral-500">지도를 움직여 핀을 원하는 위치에 맞춰 주세요.</p>
        <div role="status" className="min-h-10">
          {current === null ? (
            <p className="text-sm text-neutral-400">주소를 확인하고 있어요…</p>
          ) : current.failed ? (
            <p className="text-sm text-neutral-500">주소를 불러오지 못했어요. 이 위치 그대로 설정할 수 있어요.</p>
          ) : address?.displayName ? (
            <>
              <p className="font-semibold">{address.displayName}</p>
              {subAddress && subAddress !== address.displayName && <p className="text-xs text-neutral-500">{subAddress}</p>}
            </>
          ) : (
            <p className="text-sm text-neutral-500">주소를 찾을 수 없는 위치예요.</p>
          )}
        </div>
        <Button onClick={confirm} disabled={current === null}>
          {confirmLabel}
        </Button>
      </div>
    </div>
  )
}
