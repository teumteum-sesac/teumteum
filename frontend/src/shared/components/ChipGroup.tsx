import type { ReactNode } from 'react'
import ArrowScrollRow from './ArrowScrollRow'

interface ChipGroupProps {
  children: ReactNode
  /** true면 가로 스크롤, false면 줄바꿈 */
  scroll?: boolean
  /** 가로 스크롤일 때 양 끝에 < > 버튼을 둔다. 더 넘길 칩이 있는 쪽에만 보인다 (대분류 줄) */
  arrows?: boolean
}

/*
 * 칩 여러 개를 한 줄(가로 스크롤) 또는 여러 줄로 놓는다.
 * 가로 스크롤 영역(overflow)은 밖으로 나온 부분을 잘라 내서 z-index 로는 못 살린다.
 * 그래서 위아래(py-1)·좌우(px-1.5)에 여백을 넣고 같은 만큼 음수 margin 으로 빼 둔다:
 * 칩 자리는 그대로이고, 선택된 칩의 아래 그림자·통 튀는 효과(scale 1.08)가 양 끝에서도 잘리지 않는다
 * arrows 를 켜면 양 끝 < > 버튼 (ArrowScrollRow)
 */
/** 가로 스크롤 줄: 칩이 커지거나 그림자가 져도 잘리지 않게 사방에 여유 (음수 margin 으로 자리는 그대로) */
const SCROLL_ROOM = '-mx-1.5 -my-1 flex gap-2 px-1.5 py-1'

export default function ChipGroup({ children, scroll = true, arrows = false }: ChipGroupProps) {
  if (!scroll) return <div className="flex flex-wrap gap-2">{children}</div>
  if (arrows) {
    return (
      <ArrowScrollRow className={SCROLL_ROOM} itemName="카테고리">
        {children}
      </ArrowScrollRow>
    )
  }
  return <div className={`overflow-x-auto ${SCROLL_ROOM}`}>{children}</div>
}
