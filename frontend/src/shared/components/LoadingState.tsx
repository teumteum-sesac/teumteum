import LoadingDots from './LoadingDots'
import RaceLoadingBar from './RaceLoadingBar'
import StickerImage from './StickerImage'

/*
 * [이 파일이 하는 일]
 * 공통 로딩 화면 (NFR-021). 추천 계산·API 응답을 기다리는 동안 보여 준다.
 * 디자인: 함께 출발하는 토끼·거북이 장면 + 안내 문구(말줄임표가 . .. ... .. . 로 바뀜)
 *        + 토끼·거북이 달리기 로딩 바 (스플래시와 같은 RaceLoadingBar, S → 1 → 2 → 깃발 한 판).
 *
 *   const race = useRaceLoading(loading)      // 깃발에 도착한 뒤 넘어가게 (최소 2초, 늦으면 그만큼 더)
 *   if (race.show) return <LoadingState message="코스를 찾고 있어요" readyAt={race.readyAt} />
 *
 * readyAt 을 안 주면 2 에서 토끼가 계속 잔다 (useRaceLoading 없이 쓰는 화면은 응답이 오면 바로 바뀐다)
 */
export default function LoadingState({ message = '잠시만 기다려 주세요', readyAt = null }: { message?: string; readyAt?: number | null }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-16 text-center">
      <StickerImage name="sceneLoading" className="w-56" ratio="aspect-[3/2]" />
      <p className="font-display text-lg">
        {message}
        <LoadingDots />
      </p>
      {/* S자 트랙. 위로 튀어나오는 핀·캐릭터 자리만큼 띄운다 */}
      <RaceLoadingBar readyAt={readyAt} className="mt-8" />
    </div>
  )
}
