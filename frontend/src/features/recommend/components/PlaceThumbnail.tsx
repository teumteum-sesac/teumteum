import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import StickerImage from '../../../shared/components/StickerImage'
import type { CoursePlace } from '../types'

/** 장소 사진. 없으면 카테고리 스티커 (RES-01 ③) */
export default function PlaceThumbnail({ place, className = 'size-14' }: { place: CoursePlace; className?: string }) {
  if (place.imageUrl) return <img src={place.imageUrl} alt="" className={`shrink-0 rounded-xl object-cover ${className}`} />
  return (
    <div aria-hidden className={`flex shrink-0 items-center justify-center rounded-xl bg-primary-light/50 p-1.5 ${className}`}>
      <StickerImage name={CATEGORY_STICKER[place.category]} className="size-full" />
    </div>
  )
}
