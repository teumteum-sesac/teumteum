import LoadingDots from './LoadingDots'
import RaceLoadingBar from './RaceLoadingBar'
import StickerImage from './StickerImage'

/*
 * [이 파일이 하는 일]
 * 공통 로딩 화면 (NFR-021). 추천 계산·API 응답을 기다리는 동안 보여 준다.
 * 디자인: 함께 출발하는 토끼·거북이 장면 + 안내 문구(말줄임표가 . .. ... .. . 로 바뀜)
 *        + 토끼·거북이 달리기 로딩 바 (스플래시와 같은 RaceLoadingBar). 응답이 늦으면 처음부터 다시 달린다.
 *
 *   {loading && <LoadingState message="코스를 찾고 있어요" />}
 *
 * 달리는 모습이 한 판은 보이도록, 추천 요청은 최소 RACE_MS 동안 로딩을 유지한다 (recommend/store.ts)
 */
export default function LoadingState({ message = '잠시만 기다려 주세요' }: { message?: string }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-16 text-center">
      <StickerImage name="sceneLoading" className="w-56" ratio="aspect-[3/2]" />
      <p className="font-display text-lg">
        {message}
        <LoadingDots />
      </p>
      {/* 위로 튀어나오는 캐릭터 자리(약 44px)만큼 띄운다 */}
      <RaceLoadingBar loop className="mt-12" />
    </div>
  )
}
