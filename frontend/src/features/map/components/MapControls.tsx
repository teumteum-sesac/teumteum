import type { CSSProperties } from 'react'

/** 내 위치 버튼 상태. 따라가기·방향 보기 같은 추가 상태는 두지 않는다 */
export type LocateStatus = 'default' | 'off'

/** 컨트롤 묶음 높이(px): 확대·축소 93 + 간격 12 + 내 위치 48. 공간이 부족한지 판단할 때 쓴다 */
export const MAP_CONTROLS_HEIGHT = 93 + 12 + 48

// 홈 지도 컨트롤 디자인 토큰 (홈 지도 컨트롤 스펙 3장)
// 테두리 2px ink · 그림자 0 2px 0 ink · 모서리 14px · 포커스 링은 키보드 포커스(:focus-visible)에서만
const FOCUS_RING = 'focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#D63F7C]'
const PANEL = 'box-border w-12 rounded-[14px] border-2 border-[#2B2420] bg-white shadow-[0_2px_0_#2B2420]'

interface MapControlsProps {
  locateStatus: LocateStatus
  onLocate: () => void
  onZoomIn: () => void
  onZoomOut: () => void
  /** 최대 확대일 때 + 비활성 */
  zoomInDisabled: boolean
  /** 기본 크기(가장 멀리 본 상태)일 때 − 비활성 */
  zoomOutDisabled: boolean
  /** 공간이 부족하면 숨긴다 (150ms 페이드, 숨김 중에는 누를 수도 포커스할 수도 없음) */
  hidden: boolean
  /** 위치 (HomeScreen 이 하단 패널 윗변 기준으로 계산) */
  style?: CSSProperties
}

/** 확대(+)·축소(−) 아이콘. 20px, 선 굵기 2.4 */
function ZoomIcon({ plus }: { plus: boolean }) {
  return (
    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
      <path d="M4 10h12" />
      {plus && <path d="M10 4v12" />}
    </svg>
  )
}

/** 내 위치 조준점 아이콘 (원 + 가운데 점 + 4방향 눈금). off 면 대각선 사선 */
function LocateIcon({ off }: { off: boolean }) {
  return (
    <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
      <circle cx="12" cy="12" r="7" />
      <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
      {off ? <path d="M4 4l16 16" /> : <circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none" />}
    </svg>
  )
}

/**
 * 홈 지도 컨트롤 (HOM-01): 확대·축소 묶음 + 내 위치 버튼.
 * 위 → 아래: 확대·축소 → 간격 12px → 내 위치 (엄지에 가까운 아래쪽에 자주 쓰는 내 위치)
 */
export default function MapControls({
  locateStatus,
  onLocate,
  onZoomIn,
  onZoomOut,
  zoomInDisabled,
  zoomOutDisabled,
  hidden,
  style,
}: MapControlsProps) {
  const off = locateStatus === 'off'
  // 확대·축소 안쪽 버튼 (44×44). 비활성은 진짜 disabled, 누름 효과 없음
  const zoomButton = `flex size-11 items-center justify-center text-[#2B2420] transition-transform duration-[80ms] enabled:active:translate-y-[2px] disabled:cursor-default disabled:text-[#B5AA98] ${FOCUS_RING}`

  return (
    <div
      inert={hidden}
      style={style}
      className={`absolute z-[5] flex flex-col items-end gap-3 transition-opacity duration-150 ${hidden ? 'pointer-events-none opacity-0' : 'opacity-100'}`}
    >
      <div role="group" aria-label="지도 확대·축소" className={`${PANEL} flex flex-col items-center`}>
        <button type="button" aria-label="지도 확대" disabled={zoomInDisabled} onClick={onZoomIn} className={`${zoomButton} rounded-t-[12px]`}>
          <ZoomIcon plus />
        </button>
        <div aria-hidden="true" className="h-[1.5px] w-7 bg-[#E3D8C5]" />
        <button type="button" aria-label="지도 축소" disabled={zoomOutDisabled} onClick={onZoomOut} className={`${zoomButton} rounded-b-[12px]`}>
          <ZoomIcon plus={false} />
        </button>
      </div>

      {/* 꺼짐이어도 disabled 가 아니다. 눌러서 켜는 방법을 볼 수 있어야 한다 */}
      <button
        type="button"
        aria-label={off ? '위치 꺼짐, 켜는 방법 보기' : '내 위치로 이동'}
        onClick={onLocate}
        className={`${PANEL} flex h-12 items-center justify-center transition-[transform,box-shadow] duration-[80ms] active:translate-y-[2px] active:shadow-none ${off ? 'text-[#8C8173]' : 'text-[#2B2420]'} ${FOCUS_RING}`}
      >
        <LocateIcon off={off} />
      </button>
    </div>
  )
}
