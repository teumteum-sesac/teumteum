import type { ReactNode } from 'react'
import { Link } from 'react-router'

/** 장소 상세(DET-01)로 넘기는 값. 상세 API(GET /api/places/detail)가 장소 ID 외에 장소명·좌표를 받는다 */
export interface PlaceDetailState {
  name: string
  lat: number
  lng: number
}

interface PlaceCardProps {
  /** 목록 스크롤·강조에 쓰는 값 (data-place-id) */
  id: string
  /** 왼쪽 큰 아이콘 (카테고리) */
  icon: ReactNode
  name: string
  /** 이름 아래 한 줄 (카테고리 · 거리 등) */
  meta: ReactNode
  /** 이름 옆 작은 표시 (예: 검색 결과 '반경 내') */
  badge?: ReactNode
  selected: boolean
  /** 카드를 누르면: 지도 이동 + 마커 강조 + 카드 선택 (상세로 가지 않음) */
  onSelect: () => void
  /**
   * 선택된 카드에만 나오는 '상세 보기' 주소. 카카오 장소 ID 가 없는 결과(주소)는 null → 버튼 없음.
   * 상세 화면 연결은 DET-01 담당 작업이라 기존 주소 규칙(/results/place-{카카오 장소 ID})만 맞추고, 필요한 값은 state 로 넘긴다
   */
  detailTo: string | null
  detailState?: PlaceDetailState
}

/**
 * 주변 탐색(HOM-02) 장소 카드. 주변 장소 목록과 검색 결과가 함께 쓴다.
 * 카드 선택(지도 이동·강조)과 상세 이동('상세 보기' 버튼)을 나눠, 카드를 눌러도 화면을 떠나지 않는다
 */
export default function PlaceCard({ id, icon, name, meta, badge, selected, onSelect, detailTo, detailState }: PlaceCardProps) {
  return (
    <li data-place-id={id}>
      <div
        className={`flex items-center gap-2 rounded-xl border pr-3 ${selected ? 'border-primary bg-primary-light' : 'border-line bg-white'}`}
      >
        <button
          type="button"
          onClick={onSelect}
          aria-pressed={selected}
          className="flex min-w-0 flex-1 items-center gap-3 rounded-xl p-3 text-left"
        >
          <span aria-hidden className="flex size-14 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-2xl">
            {icon}
          </span>
          <span className="flex min-w-0 flex-col justify-center">
            <span className="flex items-center gap-1.5">
              <span className="truncate font-semibold">{name}</span>
              {badge}
            </span>
            <span className="truncate text-xs text-neutral-500">{meta}</span>
          </span>
        </button>
        {selected && detailTo && (
          <Link
            to={detailTo}
            state={detailState}
            className="shrink-0 rounded-full bg-primary px-3 py-2 text-xs font-bold text-white active:translate-y-px"
          >
            상세 보기
          </Link>
        )}
      </div>
    </li>
  )
}
