import { useEffect, useState } from 'react'
import splashUrl from '../../assets/splash/splash.webp'
import StickerImage from './StickerImage'

/*
 * [이 파일이 하는 일]
 * 사이트에 처음 들어올 때 잠깐 보이는 스플래시 화면 (토끼·거북이 + '틈틈' 로고).
 *   - 탭(세션)마다 한 번만 보인다. 같은 탭에서 새로고침·화면 이동할 때는 다시 안 뜬다 (sessionStorage)
 *   - 2.1초 동안 로딩 바를 보여 주고, 0.4초 동안 서서히 사라진다 (총 약 2.5초). 화면을 누르면 바로 넘어간다
 *   - 앱 화면은 뒤에서 그대로 그려지고 있어서, 스플래시 때문에 로딩이 늦어지지 않는다
 *
 * 로딩 바: 토끼와 거북이 달리기 (애니메이션은 index.css 의 splash-* 클래스)
 *   토끼가 먼저 앞서 달리다가 중간에 쉬고(z z), 거북이가 꾸준히 따라온다. 핑크로 채워지는 바는 거북이를 따라간다
 *
 * 이미지: src/assets/splash/splash.webp (원본 PNG 1.9MB → WebP 46KB)
 * 배경색은 이미지 가장자리 색(#fef3df)과 맞춰서, PC 처럼 화면 비율이 달라도 이음새가 안 보인다
 */
const SEEN_KEY = 'teumteum-splash-seen'
const SHOW_MS = 2100
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

  // 달리기 애니메이션 길이 = 표시 시간 (결승선에 닿을 때쯤 사라지기 시작)
  const run = { animationDuration: `${SHOW_MS}ms` }

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
        <div className="absolute inset-x-0 bottom-[11%] flex flex-col items-center gap-2.5" role="progressbar" aria-label="불러오는 중">
          <div className="relative h-2.5 w-[62%] max-w-[280px] rounded-full border-2 border-ink bg-cream">
            <div className="splash-fill h-full rounded-full bg-primary" style={run} />

            {/* 달리기 줄: 바와 같은 너비의 줄을 통째로 옮긴다. 캐릭터는 줄의 왼쪽 끝(=현재 위치)에 가운데 정렬 */}
            {/* 거북이 (뒤) */}
            <div className="splash-turtle pointer-events-none absolute inset-x-0 bottom-1.5" style={run}>
              <div className="w-fit -translate-x-1/2">
                <div className="splash-turtle-body origin-bottom">
                  <StickerImage name="turtleHead" className="size-9" />
                </div>
              </div>
            </div>

            {/* 토끼 (앞): 쉬는 동안 z z */}
            <div className="splash-rabbit pointer-events-none absolute inset-x-0 bottom-1.5 z-10" style={run}>
              <div className="relative w-fit -translate-x-1/2">
                <span className="splash-zzz absolute -top-3 -right-2 font-display text-sm text-sky" style={run}>
                  z<span className="text-[10px]">z</span>
                </span>
                <div className="splash-rabbit-body origin-bottom" style={run}>
                  <StickerImage name="rabbitHead" className="size-10" />
                </div>
              </div>
            </div>
          </div>
          <p className="font-display text-sm text-neutral-500">출발 준비 중…</p>
        </div>
      </div>
    </div>
  )
}
