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
  // 카카오 장소 페이지(iframe)가 다 뜰 때까지 빛이 지나가는 자리표시를 덮어 둔다
  const [loaded, setLoaded] = useState(false)
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
        <div className="relative h-full w-full">
          <iframe
            src={url}
            title={placeName ? `${placeName} 카카오맵 상세 정보와 리뷰` : '카카오맵 상세 정보와 리뷰'}
            onLoad={() => setLoaded(true)}
            onError={() => setFailed(true)}
            className="block h-full w-full border-0"
          />
          {!loaded && (
            <div role="status" aria-label="카카오맵 정보를 불러오는 중" className="absolute inset-0 flex flex-col gap-3 bg-cream p-4">
              <span aria-hidden className="skeleton-shimmer block h-36 rounded-2xl" />
              <span aria-hidden className="skeleton-shimmer block h-5 w-2/3 rounded-full" />
              <span aria-hidden className="skeleton-shimmer block h-4 w-1/2 rounded-full" />
              <span aria-hidden className="skeleton-shimmer block h-4 w-3/4 rounded-full" />
              <span aria-hidden className="skeleton-shimmer block h-20 rounded-2xl" />
            </div>
          )}
        </div>
      )}
    </BottomSheet>
  )
}
