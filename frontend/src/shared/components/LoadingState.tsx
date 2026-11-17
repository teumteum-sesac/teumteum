import StickerImage from './StickerImage'

/*
 * [이 파일이 하는 일]
 * 공통 로딩 화면 (NFR-021). 추천 계산·API 응답을 기다리는 동안 보여 준다.
 * 디자인: 함께 출발하는 토끼·거북이 장면이 통통 튀고, 아래에 안내 문구 + 점 3개가 차례로 깜빡인다.
 *
 *   {loading && <LoadingState message="코스를 찾고 있어요" />}
 */
export default function LoadingState({ message = '잠시만 기다려 주세요' }: { message?: string }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-16 text-center">
      <StickerImage name="sceneLoading" className="w-60 animate-bounce [animation-duration:1.4s]" ratio="aspect-[3/2]" />
      <p className="flex items-center gap-1 font-display text-lg">
        {message}
        <span aria-hidden className="flex gap-0.5">
          {[0, 1, 2].map((i) => (
            <span key={i} className="size-1.5 animate-pulse rounded-full bg-primary" style={{ animationDelay: `${i * 0.2}s` }} />
          ))}
        </span>
      </p>
    </div>
  )
}
