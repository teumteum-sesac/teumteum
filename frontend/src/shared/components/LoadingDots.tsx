import { useEffect, useState } from 'react'

/*
 * [이 파일이 하는 일]
 * "불러오는 중" 문구 뒤의 말줄임표를 . → .. → ... → .. → . 순서로 바꿔 가며 보여 준다.
 *   <p>코스를 찾고 있어요<LoadingDots /></p>
 * 점 개수가 바뀌어도 문장이 좌우로 흔들리지 않게 점 3개 너비를 미리 잡아 둔다.
 */
const FRAMES = ['.', '..', '...', '..']
const STEP_MS = 300

export default function LoadingDots() {
  const [frame, setFrame] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => setFrame((f) => (f + 1) % FRAMES.length), STEP_MS)
    return () => clearInterval(timer)
  }, [])

  return (
    <span aria-hidden className="inline-block w-[1.1em] text-left">
      {FRAMES[frame]}
    </span>
  )
}
