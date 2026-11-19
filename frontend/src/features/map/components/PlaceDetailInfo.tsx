import { useState } from 'react'
import Icon from '../../../shared/components/Icon'
import type { PlaceDetailResponse } from '../types'

/*
 * [이 파일이 하는 일]
 * 장소 상세 API(REQ-017) 결과 중 사진·영업시간·쉬는 날·소개를 보여 준다.
 * TourAPI 에 없는 장소(tourInfoAvailable = false)는 안내 문구만 보여 주고,
 * 카카오맵 링크는 화면의 [카카오맵에서 보기] 버튼이 맡는다.
 * 확인되지 않은 정보는 만들지 않는다 (NFR-026): 값이 없는 칸은 아예 그리지 않는다.
 */

interface PlaceDetailInfoProps {
  detail: PlaceDetailResponse | null
  loading: boolean
  /** 불러오기 실패 문구 (ApiError.message) */
  error: string | null
  /** 사진 대체 텍스트 */
  placeName: string
}

/** 소개가 이 글자 수보다 길면 접어 두고 [더 보기] */
const OVERVIEW_PREVIEW_LENGTH = 120

export default function PlaceDetailInfo({ detail, loading, error, placeName }: PlaceDetailInfoProps) {
  if (loading) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true" aria-label="장소 정보를 불러오는 중">
        <div className="h-48 w-full animate-pulse rounded-2xl bg-neutral-200/70" />
        <div className="h-4 w-2/3 animate-pulse rounded bg-neutral-200/70" />
        <div className="h-4 w-1/2 animate-pulse rounded bg-neutral-200/70" />
      </div>
    )
  }

  if (error) {
    return <p className="rounded-2xl bg-neutral-100 px-4 py-3 text-sm text-neutral-500">{error}</p>
  }

  if (!detail || !detail.tourInfoAvailable) {
    return (
      <p className="rounded-2xl bg-neutral-100 px-4 py-3 text-sm text-neutral-500">
        사진·영업시간 정보가 없는 장소예요. 카카오맵에서 자세한 정보를 확인해 주세요.
      </p>
    )
  }

  const { imageUrl, imageCopyrightType, openingHours, restDate, overview } = detail
  const hasHours = Boolean(openingHours || restDate)

  return (
    <div className="flex flex-col gap-3">
      {imageUrl && (
        <figure className="overflow-hidden rounded-2xl border-2 border-line">
          <img src={imageUrl} alt={placeName} loading="lazy" className="h-48 w-full object-cover" />
          <figcaption className="bg-cream px-3 py-1 text-right text-[11px] text-neutral-500">
            출처: 한국관광공사{formatCopyright(imageCopyrightType)}
          </figcaption>
        </figure>
      )}

      {hasHours && (
        <section className="paper-card flex flex-col gap-2 p-4">
          {openingHours && <InfoRow icon="clock" label="영업시간" value={openingHours} />}
          {restDate && <InfoRow icon="flag" label="쉬는 날" value={restDate} />}
        </section>
      )}

      {overview && <Overview text={overview} />}
    </div>
  )
}

function InfoRow({ icon, label, value }: { icon: 'clock' | 'flag'; label: string; value: string }) {
  return (
    <div className="flex items-start gap-2 text-sm">
      <Icon name={icon} className="mt-0.5 size-4 shrink-0 text-primary-dark" />
      <span className="w-14 shrink-0 font-bold">{label}</span>
      {/* TourAPI 는 줄바꿈(\n)·<br> 로 여러 줄을 보낸다 */}
      <span className="min-w-0 flex-1 whitespace-pre-line text-neutral-700">{toPlainText(value)}</span>
    </div>
  )
}

function Overview({ text }: { text: string }) {
  const plain = toPlainText(text)
  const long = plain.length > OVERVIEW_PREVIEW_LENGTH
  const [open, setOpen] = useState(false)

  return (
    <section className="px-1">
      <h3 className="mb-1 text-sm font-bold">소개</h3>
      <p className="text-sm leading-relaxed whitespace-pre-line text-neutral-700">
        {long && !open ? `${plain.slice(0, OVERVIEW_PREVIEW_LENGTH)}…` : plain}
      </p>
      {long && (
        <button type="button" onClick={() => setOpen((v) => !v)} className="mt-1 text-xs text-neutral-500 underline underline-offset-4">
          {open ? '접기' : '더 보기'}
        </button>
      )}
    </section>
  )
}

/** 공공누리 유형 표시. "Type3" → " (공공누리 제3유형)", 모르는 값은 그대로, 없으면 빈 문자열 */
function formatCopyright(type: string | null): string {
  if (!type) return ''
  const num = /^Type(\d)$/i.exec(type)?.[1]
  return ` (공공누리 ${num ? `제${num}유형` : type})`
}

/** TourAPI 문자열의 <br> 를 줄바꿈으로, 나머지 태그는 지우고 &amp; 같은 기본 엔티티를 되돌린다 (HTML 로 넣지 않는다) */
function toPlainText(value: string): string {
  return value
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
    .trim()
}
