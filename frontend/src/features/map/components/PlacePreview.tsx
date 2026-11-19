import { useRef } from 'react'
import { useNavigate } from 'react-router'
import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import Button from '../../../shared/components/Button'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import KakaoPlaceSheet from '../../../shared/components/KakaoPlaceSheet'
import { getCategoryLabel, type CategoryCode } from '../../../shared/types/domain'
import { useConditionStore } from '../../condition/store'
import { formatDistance } from '../format'
import KakaoMap from './KakaoMap'

/*
 * [이 파일이 하는 일]
 * 장소 미리보기 (/places/:placeId). 홈 검색·주변 탐색에서 장소를 누르면 여기로 온다.
 * 장소 상세 API(REQ-017, 정종일)가 나오기 전이라, 목록에서 이미 받은 정보(이름·카테고리·주소·좌표·거리)를
 * 화면 이동 때 같이 넘겨받아(location.state) 그대로 보여 준다. 사진·소개는 API 가 나오면 이 화면에 채운다.
 *
 * 버튼: [여기서 출발해 코스 추천받기] = 이 장소를 출발지로 외출 조건(CON-01)
 * 하단 시트는 항상 표시하며 손잡이로 최소화·펼치기 한다. 카카오 장소 페이지의 영업시간·리뷰를 그대로 보여 준다.
 */
export interface PlacePreviewState {
  kakaoPlaceId: string
  name: string
  /** 우리 대분류. 홈 검색 결과처럼 모를 수 있다 */
  category?: CategoryCode | null
  /** 세분류 또는 카카오 카테고리 이름 */
  subCategory?: string | null
  address?: string | null
  latitude: number
  longitude: number
  /** 현재 위치에서 거리(m) */
  distance?: number | null
}

export default function PlacePreview({ placeId, place }: { placeId: string; place: PlacePreviewState | null }) {
  const navigate = useNavigate()
  const setOrigin = useConditionStore((s) => s.setOrigin)
  const descriptionRef = useRef<HTMLParagraphElement>(null)

  // 새로고침·주소 직접 입력으로 들어오면 넘겨받은 정보가 없다 → 카카오맵 링크로 안내
  if (!place || place.kakaoPlaceId !== placeId) {
    return (
      <EmptyState
        sticker="sceneEmpty"
        message="장소 정보를 다시 불러올 수 없어요"
        description="목록에서 장소를 다시 눌러 주세요. 카카오맵에서도 볼 수 있어요."
        action={
          <>
            <Button className="mx-auto w-auto px-8" onClick={() => navigate('/nearby')}>
              주변 장소 보기
            </Button>
            <KakaoPlaceSheet kakaoPlaceId={placeId} />
          </>
        }
      />
    )
  }

  const center = { lat: place.latitude, lng: place.longitude }

  return (
    <div className="flex flex-col gap-4 p-4">
      <section className="paper-card flex items-center gap-3 p-4">
        <span className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-primary-light/60 p-1.5">
          {place.category ? (
            <StickerImage name={CATEGORY_STICKER[place.category]} className="size-full" />
          ) : (
            <Icon name="location" className="size-8 text-primary-dark" />
          )}
        </span>
        <div className="min-w-0">
          <h2 className="font-display text-xl leading-snug">{place.name}</h2>
          <p className="mt-0.5 text-xs text-neutral-500">
            {[place.category ? getCategoryLabel(place.category) : null, place.subCategory].filter(Boolean).join(' › ')}
          </p>
          {place.distance != null && (
            <span className="mt-1 inline-block rounded-full bg-sunny/60 px-2 py-0.5 text-[11px] font-bold">
              여기서 {formatDistance(place.distance)}
            </span>
          )}
        </div>
      </section>

      <div className="overflow-hidden rounded-2xl border-2 border-line">
        <KakaoMap center={center} level={3} markers={[{ lat: center.lat, lng: center.lng, title: place.name }]} className="h-52 w-full" />
      </div>

      {place.address && (
        <p className="flex items-start gap-2 px-1 text-sm text-neutral-700">
          <Icon name="location" className="mt-0.5 size-4 text-primary-dark" />
          {place.address}
        </p>
      )}

      <div className="mt-2 flex flex-col gap-2">
        <Button
          variant="pink"
          className="flex items-center justify-center gap-1.5"
          onClick={() => {
            setOrigin({ name: place.name, address: place.address ?? undefined, lat: place.latitude, lng: place.longitude })
            navigate('/condition')
          }}
        >
          여기서 출발해 코스 추천받기
          <Icon name="chevronRight" className="size-5" strokeWidth={2.8} />
        </Button>
      </div>
      <p ref={descriptionRef} className="px-1 text-xs text-neutral-400">사진·소개는 장소 상세 정보가 준비되면 여기에 함께 보여 드려요.</p>
      <KakaoPlaceSheet
        kakaoPlaceId={place.kakaoPlaceId}
        placeName={place.name}
        initialTopRef={descriptionRef}
      />
    </div>
  )
}
