import type { CSSProperties } from 'react'

/** 내 위치 버튼 상태. 따라가기·방향 보기 같은 추가 상태는 두지 않는다 */
export type LocateStatus = 'default' | 'off'

/** 버튼 사이 간격(px): 확대·축소 그룹과 현재 위치 버튼 */
const GROUP_GAP = 10

/** 컨트롤 묶음 높이(px): 확대·축소 89(44 + 구분선 1 + 44) + 간격 10 + 현재 위치 44. 공간이 부족한지 판단할 때 쓴다 */
export const MAP_CONTROLS_HEIGHT = 89 + GROUP_GAP + 44

// 지도 컨트롤 디자인 (최종 추천안): 흰색 미니멀 버튼 + 핑크 현재 위치 버튼만 포인트
// 포커스 링은 키보드 포커스(:focus-visible)에서만
const FOCUS_RING = 'focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#D63F7C]'
// Tailwind 는 소스의 클래스 문자열을 그대로 찾아 CSS 를 만들므로 클래스는 조합하지 말고 완성된 문자열로 둔다
/** 현재 위치 버튼: 약한 그림자 */
const LOCATE_SHADOW = 'shadow-[0_2px_8px_rgba(32,32,32,0.12)]'
/** 확대·축소 그룹: 아주 연한 베이지 1px 테두리(box-shadow) + 약한 그림자 */
const ZOOM_GROUP_SHADOW = 'shadow-[0_0_0_1px_#E6DAC2,0_2px_8px_rgba(32,32,32,0.12)]'

/** 확대(+)·축소(−) 아이콘. 20px */
function ZoomIcon({ plus }: { plus: boolean }) {
  return (
    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
      <path d="M4 10h12" />
      {plus && <path d="M10 4v12" />}
    </svg>
  )
}

/** locate-fixed 아이콘 (원 + 가운데 원 + 4방향 눈금). off 면 대각선 사선 */
function LocateIcon({ off }: { off: boolean }) {
  return (
    <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M2 12h3M19 12h3M12 2v3M12 19v3" />
      <circle cx="12" cy="12" r="7" />
      {off ? <path d="M4 4l16 16" /> : <circle cx="12" cy="12" r="3" />}
    </svg>
  )
}

interface LocateButtonProps {
  status: LocateStatus
  onClick: () => void
}

/**
 * 현재 위치 버튼 (HOM-01·HOM-02 공통). 44×44 흰 원 + 핑크 테두리 + 진한 핑크 locate-fixed 아이콘.
 * 꺼짐이어도 disabled 가 아니다. 눌러서 켜는 방법(위치 안내 카드)을 볼 수 있어야 한다
 */
export function LocateButton({ status, onClick }: LocateButtonProps) {
  const off = status === 'off'
  return (
    <button
      type="button"
      aria-label={off ? '위치 꺼짐, 켜는 방법 보기' : '내 위치로 이동'}
      onClick={onClick}
      className={`box-border flex size-11 items-center justify-center rounded-full border-[1.5px] bg-white ${LOCATE_SHADOW} transition-transform duration-[80ms] active:scale-95 ${
        off ? 'border-[#D9CFC0] text-[#8C8173]' : 'border-[#F26FA8] text-[#C93D7A]'
      } ${FOCUS_RING}`}
    >
      <LocateIcon off={off} />
    </button>
  )
}

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

/**
 * 홈 지도 컨트롤 (HOM-01): 확대·축소 그룹(위) + 현재 위치 버튼(아래).
 * 확대·축소는 흰 배경 · 아주 연한 베이지 테두리 · 약한 그림자의 세로 그룹, 두 버튼 사이에 얇은 구분선
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
  // 확대·축소 버튼 (44×44 터치 영역). 비활성은 진짜 disabled, 누름 효과 없음
  const zoomButton = `flex size-11 items-center justify-center text-[#202020] enabled:active:bg-[#F6F1E7] disabled:cursor-default disabled:text-[#C9C1B3] ${FOCUS_RING}`

  return (
    <div
      inert={hidden}
      style={{ gap: GROUP_GAP, ...style }}
      className={`absolute z-[5] flex flex-col items-center transition-opacity duration-150 ${hidden ? 'pointer-events-none opacity-0' : 'opacity-100'}`}
    >
      {/* 테두리는 box-shadow 로 그려 버튼 터치 영역(44px)을 줄이지 않는다 */}
      <div
        role="group"
        aria-label="지도 확대·축소"
        className={`flex w-11 flex-col items-center rounded-2xl bg-white ${ZOOM_GROUP_SHADOW}`}
      >
        <button type="button" aria-label="지도 확대" disabled={zoomInDisabled} onClick={onZoomIn} className={`${zoomButton} rounded-t-2xl`}>
          <ZoomIcon plus />
        </button>
        <div aria-hidden="true" className="h-px w-6 bg-[#EFE6D6]" />
        <button type="button" aria-label="지도 축소" disabled={zoomOutDisabled} onClick={onZoomOut} className={`${zoomButton} rounded-b-2xl`}>
          <ZoomIcon plus={false} />
        </button>
      </div>

      <LocateButton status={locateStatus} onClick={onLocate} />
    </div>
  )
}
