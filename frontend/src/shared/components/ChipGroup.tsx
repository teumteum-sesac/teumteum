import type { ReactNode } from 'react'

interface ChipGroupProps {
  children: ReactNode
  /** true면 가로 스크롤, false면 줄바꿈 */
  scroll?: boolean
}

/*
 * 칩 여러 개를 한 줄(가로 스크롤) 또는 여러 줄로 놓는다.
 * 가로 스크롤일 때 위아래 여백(py-1)을 조금 준다: 선택된 칩의 아래 그림자·통 튀는 효과가 스크롤 영역에 잘리지 않게
 */
export default function ChipGroup({ children, scroll = true }: ChipGroupProps) {
  return (
    <div className={`flex gap-2 ${scroll ? '-my-1 overflow-x-auto py-1' : 'flex-wrap'}`}>{children}</div>
  )
}
