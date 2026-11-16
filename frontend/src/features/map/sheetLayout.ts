/** 주변 장소 탐색 바텀시트 높이 단계 (HOM-02): 접힘 / 반만 / 전체. 기본은 반만 */
export type SheetLevel = 'collapsed' | 'half' | 'full'

/** 아래 → 위 순서. 드래그·클릭으로 한 단계씩 이동한다 */
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
