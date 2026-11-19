import StickerImage from './StickerImage'

/*
 * [이 파일이 하는 일]
 * 토끼·거북이 달리기 로딩 바. 스플래시(Splash)와 공통 로딩 화면(LoadingState)이 같이 쓴다.
 *   토끼가 깡충깡충 앞서 달리다 중간에 졸고(z z), 거북이가 꾸준히 따라오면 깨서 결승점으로.
 *   핑크로 채워지는 바는 거북이 위치를 따라간다.
 *
 *   <RaceLoadingBar />        한 판 달리고 결승점에서 멈춤 (스플래시)
 *   <RaceLoadingBar loop />   응답이 늦으면 처음부터 다시 달림 (로딩 화면)
 *
 * 애니메이션 모양은 index.css 의 splash-* 클래스, 한 판 길이는 RACE_MS.
 * 위치 이동을 left 가 아니라 transform 으로 하는 이유: 몸통(깡충·뒤뚱)의 transform 애니메이션과 섞이면
 * 브라우저가 캐릭터를 그리지 않는 경우가 있었다. 바와 같은 너비의 "줄"을 통째로 translateX 로 옮긴다.
 */

/** 달리기 한 판 길이(ms). 로딩 최소 시간도 이 값을 쓴다 (한 판은 보이도록) */
export const RACE_MS = 2100

export default function RaceLoadingBar({ loop = false, className = '' }: { loop?: boolean; className?: string }) {
  const run = { animationDuration: `${RACE_MS}ms`, animationIterationCount: loop ? 'infinite' : 1 }

  return (
    <div aria-hidden className={`relative h-2.5 w-[62%] max-w-[280px] rounded-full border-2 border-ink bg-cream ${className}`}>
      <div className="splash-fill h-full rounded-full bg-primary" style={run} />

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
  )
}
