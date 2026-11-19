import LoadingDots from './LoadingDots'
import RaceLoadingBar from './RaceLoadingBar'
import StickerImage from './StickerImage'

/*
 * [이 파일이 하는 일]
 * 공통 로딩 화면 (NFR-021). 두 가지 모양:
 *   - 기본(skeleton): 화면 자리표시 회색 덩어리 위로 빛이 왼쪽 → 오른쪽으로 지나간다 (구글 로딩처럼, index.css skeleton-shimmer).
 *                     코스 상세·코스 지도·저장한 코스·장소 교체·회원 정보·관리자 등 일반 화면 전환에 쓴다
 *   - race          : 토끼·거북이 달리기 (S → 1 → 2 → 깃발). 외출 코스 추천(RES-01) 계산 중에만 쓴다
 *
 *   if (loading) return <LoadingState message="코스를 불러오는 중이에요" />               // 스켈레톤
 *   const race = useRaceLoading(loading)
 *   if (race.show) return <LoadingState variant="race" message="코스를 찾고 있어요" readyAt={race.readyAt} />
 *
 * 스켈레톤은 글씨를 화면에 쓰지 않고 message 를 화면 읽기 프로그램에만 알린다
 */
export default function LoadingState({
  message = '잠시만 기다려 주세요',
  variant = 'skeleton',
  readyAt = null,
}: {
  message?: string
  variant?: 'skeleton' | 'race'
  /** race 전용: 화면 준비가 끝난 시각 (useRaceLoading) */
  readyAt?: number | null
}) {
  if (variant === 'skeleton') return <SkeletonPage label={message} />

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

/** 화면 자리표시: 큰 덩어리(배너·지도 자리) + 제목 줄 + 카드 3개. 덩어리마다 빛이 지나간다 */
function SkeletonPage({ label }: { label: string }) {
  return (
    <div role="status" aria-live="polite" aria-label={label} className="flex flex-1 flex-col gap-4 px-4 py-5">
      <div aria-hidden className="flex flex-col gap-4">
        <span className="skeleton-shimmer block h-40 rounded-2xl" />
        <span className="skeleton-shimmer block h-5 w-1/2 rounded-full" />
        <span className="skeleton-shimmer block h-4 w-1/3 rounded-full" />
        {[0, 1, 2].map((i) => (
          <span key={i} className="flex items-center gap-3 rounded-2xl border border-line bg-white/70 p-3">
            <span className="skeleton-shimmer block size-14 shrink-0 rounded-xl" />
            <span className="flex flex-1 flex-col gap-2">
              <span className="skeleton-shimmer block h-4 w-3/5 rounded-full" />
              <span className="skeleton-shimmer block h-3 w-2/5 rounded-full" />
            </span>
          </span>
        ))}
      </div>
    </div>
  )
}
