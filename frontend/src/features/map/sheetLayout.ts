/** 주변 장소 탐색 바텀시트 높이 단계 (HOM-02): 접힘 / 반만 / 전체. 기본은 반만 */
export type SheetLevel = 'collapsed' | 'half' | 'full'

/** 아래 → 위 순서 */
export const SHEET_LEVELS: SheetLevel[] = ['collapsed', 'half', 'full']

// 시트 높이와 지도 영역의 아래 여백은 반드시 같은 값이어야 시트와 지도가 겹치지 않는다
// (화면설계서 HOM-02 ①: 시트가 올라온 만큼 지도가 줄어든다).
// Tailwind 는 소스의 클래스 문자열을 그대로 찾아 CSS 를 만들므로 문자열을 조합하지 말고 그대로 둔다.
// - 접힘 4.25rem(68px): 손잡이 28px + '내 주변 장소' 제목줄 40px 만 보인다
// - 전체 calc(100% - 9rem): 위쪽 검색창 + 위치 안내 문구(375px 에서 약 136px)에 손잡이가 가리지 않게 비운다

/** 시트 높이 (부모 = HomeScreen 의 지도 영역 `relative flex-1`) */
export const SHEET_HEIGHT_CLASS: Record<SheetLevel, string> = {
  collapsed: 'h-17',
  half: 'h-[50%]',
  full: 'h-[calc(100%-9rem)]',
}

/** 지도 영역의 아래 끝. 시트 높이만큼 비운다 */
export const MAP_BOTTOM_CLASS: Record<SheetLevel, string> = {
  collapsed: 'bottom-17',
  half: 'bottom-[50%]',
  full: 'bottom-[calc(100%-9rem)]',
}

/** 접힘 높이(px). SHEET_HEIGHT_CLASS.collapsed(h-17 = 4.25rem) 와 같은 값 */
const COLLAPSED_PX = 68
/** 전체일 때 위에 비우는 높이(px). SHEET_HEIGHT_CLASS.full(100% - 9rem) 의 9rem */
const FULL_TOP_GAP_PX = 144

/** 단계별 시트 높이(px). 끌기 중 범위 제한과 놓았을 때 가까운 단계 계산에 쓴다 */
export function getSheetLevelHeights(parentHeight: number): Record<SheetLevel, number> {
  return {
    collapsed: COLLAPSED_PX,
    half: Math.max(COLLAPSED_PX, parentHeight * 0.5),
    full: Math.max(COLLAPSED_PX, parentHeight - FULL_TOP_GAP_PX),
  }
}

/** 끌기 중 시트 높이: 접힘 ~ 전체 사이로 제한한다 */
export function clampSheetHeight(height: number, parentHeight: number): number {
  const heights = getSheetLevelHeights(parentHeight)
  return Math.min(Math.max(height, heights.collapsed), heights.full)
}

/**
 * 끌기를 놓았을 때 정착할 단계.
 * - 놓은 높이에서 가장 가까운 단계로 간다. 길게 끌면 두 단계도 한 번에 이동한다
 * - 가장 가까운 단계가 처음 단계여도 minStep(px) 이상 끌었으면 끈 방향으로 한 단계 옮긴다
 * @param dy 끈 거리(px). 아래로 끌면 양수, 위로 끌면 음수
 */
export function snapSheetLevel(from: SheetLevel, startHeight: number, dy: number, parentHeight: number, minStep: number): SheetLevel {
  const heights = getSheetLevelHeights(parentHeight)
  const height = clampSheetHeight(startHeight - dy, parentHeight)
  const nearest = SHEET_LEVELS.reduce((best, l) => (Math.abs(heights[l] - height) < Math.abs(heights[best] - height) ? l : best))
  if (nearest !== from || Math.abs(dy) < minStep) return nearest
  const index = SHEET_LEVELS.indexOf(from) + (dy < 0 ? 1 : -1)
  return SHEET_LEVELS[Math.min(Math.max(index, 0), SHEET_LEVELS.length - 1)]
}

/** 손잡이·제목을 눌렀을 때: 반만 ↔ 접힘, 전체에서는 반만 */
export function tapSheetLevel(from: SheetLevel): SheetLevel {
  return from === 'half' ? 'collapsed' : 'half'
}
