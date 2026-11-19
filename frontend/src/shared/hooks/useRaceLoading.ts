import { useEffect, useRef, useState } from 'react'
import { FINISH_TOTAL_MS, READY_CHECK_MS } from '../components/RaceLoadingBar'

/**
 * [이 파일이 하는 일]
 * 로딩 화면의 토끼가 깃발에 "도착한 뒤에" 다음 화면을 보여 주게 하는 훅. 시간표 (토끼 기준):
 *   ~1초 1 도착 → ~2초 2 도착, 여기서 화면 준비(응답) 확인
 *   준비됨    : 1초 동안 깃발까지 → 화면 전환 (빠르면 3초)
 *   준비 안 됨: 2 에서 계속 쉬다가, 준비되는 순간부터 1초 동안 깃발까지 → 화면 전환
 *
 *   const race = useRaceLoading(loading)
 *   if (race.show) return <LoadingState readyAt={race.readyAt} />
 *
 * [주의] 응답이 온 바로 그 렌더에서도 show 가 true 여야 한다.
 *   effect 에서만 판단하면 응답 직후 한 번 로딩 화면이 사라졌다가 다시 생겨서
 *   로딩 바가 새로 만들어지고(토끼가 S 부터 다시 출발) 1 근처에서 화면이 넘어가는 버그가 난다.
 *   그래서 loading 이 끝나는 순간을 렌더 중에 잡아 finishing 을 켠다 (React 의 "이전 값과 비교해 상태 조정" 방식).
 */
export function useRaceLoading(loading: boolean): { show: boolean; readyAt: number | null } {
  // 응답은 왔고 토끼가 깃발로 가는 중 (화면 전환 전까지 true)
  const [finishing, setFinishing] = useState(false)
  // 화면 준비(응답 도착) 시각. 로딩 바에 넘겨 2 에서 깨는 시각을 정한다
  const [readyAt, setReadyAt] = useState<number | null>(null)
  // 이전 렌더의 loading. 바뀌는 순간을 렌더 중에 잡는다
  const [prevLoading, setPrevLoading] = useState(loading)
  if (loading !== prevLoading) {
    setPrevLoading(loading)
    if (loading) {
      // 다시 로딩: 이전 깃발 달리기는 버리고 2 에서 기다리는 상태로
      setFinishing(false)
      setReadyAt(null)
    } else {
      setFinishing(true)
    }
  }
  // 이번 로딩이 시작된 시각 (렌더와 상관없는 값이라 ref)
  const startAt = useRef<number | null>(null)

  useEffect(() => {
    if (loading) {
      startAt.current = Date.now()
      return
    }
    if (startAt.current === null) return
    const now = Date.now()
    // 전환 시각 = (2 도착 시각과 준비 시각 중 늦은 쪽) + 깃발까지 1초
    const switchAt = Math.max(startAt.current + READY_CHECK_MS, now) + FINISH_TOTAL_MS
    startAt.current = null
    setReadyAt(now)
    const timer = setTimeout(() => {
      setFinishing(false)
      setReadyAt(null)
    }, switchAt - now)
    return () => clearTimeout(timer)
  }, [loading])

  return { show: loading || finishing, readyAt: loading ? null : readyAt }
}
