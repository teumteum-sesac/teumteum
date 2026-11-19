import { useEffect, useState } from 'react'
import splashUrl from '../../assets/splash/splash.webp'
import LoadingDots from './LoadingDots'
import RaceLoadingBar, { FINISH_TOTAL_MS, READY_CHECK_MS } from './RaceLoadingBar'

/*
 * [이 파일이 하는 일]
 * 사이트에 처음 들어올 때 잠깐 보이는 스플래시 화면 (토끼·거북이 + '틈틈' 로고).
 *   - 탭(세션)마다 한 번만 보인다. 같은 탭에서 새로고침·화면 이동할 때는 다시 안 뜬다 (sessionStorage)
 *   - 토끼가 1초에 1, 2초에 2, 3초에 깃발 도착 → 0.4초 동안 서서히 사라진다. 화면을 누르면 바로 넘어간다
 *   - 앱 화면은 뒤에서 그대로 그려지고 있어서, 스플래시 때문에 로딩이 늦어지지 않는다
 *
 * 로딩 바: 토끼와 거북이 달리기 (RaceLoadingBar, 로딩 화면과 같이 씀)
 *
 * 이미지: src/assets/splash/splash.webp (원본 PNG 1.9MB → WebP 46KB)
 * 배경색은 이미지 가장자리 색(#fef3df)과 맞춰서, PC 처럼 화면 비율이 달라도 이음새가 안 보인다
 */
const SEEN_KEY = 'teumteum-splash-seen'
/** 스플래시는 기다릴 응답이 없어 처음부터 준비된 것으로 본다 → 2초에 2 도착, 3초에 깃발 도착 후 사라지기 시작 */
const SHOW_MS = READY_CHECK_MS + FINISH_TOTAL_MS
const FADE_MS = 400

function hasSeen(): boolean {
  try {
    return sessionStorage.getItem(SEEN_KEY) === '1'
  } catch {
    return false // 사생활 보호 모드 등에서 저장소를 못 쓰면 그냥 보여 준다
  }
}

export default function Splash() {
  const [phase, setPhase] = useState<'show' | 'fade' | 'done'>(() => (hasSeen() ? 'done' : 'show'))
  // 처음부터 준비된 상태 (readyAt = 처음 보인 시각)
  const [readyAt] = useState(() => Date.now())

  useEffect(() => {
    if (phase === 'done') return
    try {
      sessionStorage.setItem(SEEN_KEY, '1')
    } catch {
      // 저장 실패해도 화면에는 영향 없음
    }
    const timer = setTimeout(() => setPhase(phase === 'show' ? 'fade' : 'done'), phase === 'show' ? SHOW_MS : FADE_MS)
    return () => clearTimeout(timer)
  }, [phase])

  if (phase === 'done') return null

  return (
    <div
      role="presentation"
      onClick={() => setPhase('fade')}
      className={`fixed inset-0 z-[100] flex justify-center bg-[#fef3df] transition-opacity ease-out ${
        phase === 'fade' ? 'opacity-0' : 'opacity-100'
      }`}
      style={{ transitionDuration: `${FADE_MS}ms` }}
    >
      <div className="relative h-full w-full max-w-[480px]">
        <img src={splashUrl} alt="틈틈" className="h-full w-full object-cover" />

        {/* 로딩 바: 이미지 아래쪽 빈 공간에 둔다 */}
        <div className="absolute inset-x-0 bottom-[3%] flex flex-col items-center gap-1" role="progressbar" aria-label="불러오는 중">
          {/* 캐릭터 그림 아래 빈 공간에 들어가게 작게 */}
          <RaceLoadingBar readyAt={readyAt} sizeClass="w-[min(52vw,230px)]" />
          <p className="font-display text-sm text-neutral-500">
            출발 준비 중
            <LoadingDots />
          </p>
        </div>
      </div>
    </div>
  )
}
