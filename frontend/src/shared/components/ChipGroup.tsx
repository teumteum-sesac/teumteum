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
 * 가로 스크롤일 때 위아래 여백(py-1)을 조금 준다: 선택된 칩의 아래 그림자·통 튀는 효과가 스크롤 영역에 잘리지 않게
 * arrows 를 켜면 양 끝 < > 버튼 (ArrowScrollRow)
 */
export default function ChipGroup({ children, scroll = true, arrows = false }: ChipGroupProps) {
  if (!scroll) return <div className="flex flex-wrap gap-2">{children}</div>
  if (arrows) {
    return (
      <ArrowScrollRow className="-my-1 flex gap-2 py-1" itemName="카테고리">
        {children}
      </ArrowScrollRow>
    )
  }
  return <div className="-my-1 flex gap-2 overflow-x-auto py-1">{children}</div>
}
