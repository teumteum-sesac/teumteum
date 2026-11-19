import { useState, type ReactNode, type RefObject } from 'react'
import BottomSheet from './BottomSheet'
import { getKakaoMapLink } from '../types/domain'

interface KakaoPlaceSheetProps {
  kakaoPlaceId?: string
  placeName?: string
  /** 저장 코스처럼 카카오 ID가 없는 경우 표시할 저장 당시 정보. */
  children?: ReactNode
  initialTopRef?: RefObject<HTMLElement | null>
}

/** 카카오 장소 페이지를 새 탭 대신 크기를 조절할 수 있는 바텀시트에 그대로 표시한다. */
export default function KakaoPlaceSheet({ kakaoPlaceId, placeName, children, initialTopRef }: KakaoPlaceSheetProps) {
  const [failed, setFailed] = useState(false)
  const url = kakaoPlaceId ? getKakaoMapLink(kakaoPlaceId) : null

  return (
    <BottomSheet title={url ? (placeName ? `${placeName} · 카카오맵` : '카카오맵 장소 정보') : (placeName ?? '장소 정보')} initialTopRef={initialTopRef}>
      {!url ? children : failed ? (
        <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
          <p className="text-sm text-neutral-500">카카오맵 정보를 불러오지 못했어요.</p>
          <button type="button" onClick={() => setFailed(false)} className="rounded-full bg-cream px-4 py-2 font-bold">다시 시도</button>
          <a href={url} className="text-sm text-primary-dark underline">현재 창에서 카카오맵 열기</a>
        </div>
      ) : (
        <iframe
          src={url}
          title={placeName ? `${placeName} 카카오맵 상세 정보와 리뷰` : '카카오맵 상세 정보와 리뷰'}
          onError={() => setFailed(true)}
          className="block h-full w-full border-0"
        />
      )}
    </BottomSheet>
  )
}
