import { useCallback, useState } from 'react'
import Toast from '../../../shared/components/Toast'
import type { LatLng } from '../../../shared/types/domain'
import { requestCurrentPosition } from '../hooks/useCurrentPosition'
import { LocateButton, type LocateStatus } from './MapControls'

interface MapLocateButtonProps {
  /** 현재 위치를 받았을 때. 지도를 그 위치로 옮기고(새 객체로 center 전달) 현재 위치 스티커를 찍는다 */
  onLocate: (position: LatLng) => void
  /** 지도 위 위치 (예: 'right-3 bottom-3'). 지도 영역(relative) 안에 absolute 로 놓인다 */
  className?: string
}

/**
 * 지도 위 현재 위치 버튼 (코스 지도·지도에서 위치 고르기·장소 미리보기 공통).
 * 모양은 홈·주변 탐색과 같은 LocateButton. 누르면 위치를 한 번 받아 onLocate 로 넘긴다.
 * 위치를 못 받으면(권한 거부·실패) 버튼이 꺼짐 모양으로 바뀌고 안내 토스트를 띄운다. 꺼진 채로 눌러도 다시 시도한다
 * 홈(HOM-01)·주변 탐색(HOM-02)은 위치 안내 카드까지 있는 자체 버튼(HomeScreen)을 쓴다
 */
export default function MapLocateButton({ onLocate, className = 'right-3 bottom-3' }: MapLocateButtonProps) {
  const [status, setStatus] = useState<LocateStatus>('default')
  const [toast, setToast] = useState(false)
  const closeToast = useCallback(() => setToast(false), [])

  const locate = async () => {
    const position = await requestCurrentPosition()
    if (position) {
      setStatus('default')
      onLocate(position)
    } else {
      setStatus('off')
      setToast(true)
    }
  }

  return (
    <>
      <div className={`absolute z-10 ${className}`}>
        <LocateButton status={status} onClick={() => void locate()} />
      </div>
      {toast && <Toast message="위치를 확인할 수 없어요. 브라우저에서 위치 권한을 허용해 주세요." onClose={closeToast} />}
    </>
  )
}
