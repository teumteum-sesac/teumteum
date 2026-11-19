/**
 * 요청이 아무리 빨리 끝나도 최소 ms 만큼은 기다렸다가 결과를 돌려준다.
 * 로딩 화면(토끼·거북이 달리기)이 깜빡하고 사라지지 않게, 한 판은 보이도록 할 때 쓴다.
 * 요청이 실패해도 최소 시간을 채운 뒤 같은 오류를 그대로 던진다.
 *
 *   const result = await withMinDuration(createRecommendation(body), RACE_MS)
 */
export async function withMinDuration<T>(promise: Promise<T>, ms: number): Promise<T> {
  const [result] = await Promise.allSettled([promise, new Promise((resolve) => setTimeout(resolve, ms))])
  if (result.status === 'rejected') throw result.reason
  return result.value
}
