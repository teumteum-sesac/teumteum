import { useEffect, type ReactNode } from 'react'
import { useLocation, useNavigate, useNavigationType } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import { TRANSPORT_STICKER } from '../../../shared/assets/stickerAssets'
import Button from '../../../shared/components/Button'
import Icon from '../../../shared/components/Icon'
import SegmentedControl from '../../../shared/components/SegmentedControl'
import StickerImage from '../../../shared/components/StickerImage'
import { MAX_CATEGORIES, TRANSPORT_OPTIONS } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { requestCurrentPosition } from '../../map/hooks/useCurrentPosition'
import { toCurrentLocation } from '../api/conditionApi'
import { useRecommendStore } from '../../recommend/store'
import { canRequestRecommend, toRecommendRequest } from '../recommendRequest'
import { DEFAULT_AVAILABLE_MINUTES, useConditionStore } from '../store'
import CategorySelector from './CategorySelector'
import LocationFields from './LocationFields'
import TimeSelector from './TimeSelector'

/** 외출 조건 설정 (CON-01): 위치 → 시간 → 이동수단 → 카테고리 → 추천받기 */
export default function ConditionForm() {
  const navigate = useNavigate()
  // 추천 결과의 [조건 바꾸기] 로 들어왔는지. 그렇다면 [추천받기] 는 결과 화면을 새로 열지 않고 뒤로 돌아간다
  // (새로 열면 조건 → 결과 → 조건 → 결과… 가 기록에 쌓여 뒤로가기를 여러 번 눌러야 홈에 간다)
  const fromResults = (useLocation().state as { fromResults?: boolean } | null)?.fromResults === true
  const { origin, destination, availableMinutes, startTime, transportType, setTransportType } = useConditionStore()
  const categoryCount = useConditionStore((s) => s.categories.length)
  const requestRecommend = useRecommendStore((s) => s.request)

  // 여유 시간은 화면에 새로 들어올 때마다 1시간으로 시작한다 (홈 등에서 진입 = PUSH).
  // 위치 설정에서 뒤로 돌아온 경우(POP)와 추천 결과의 [조건 바꾸기](fromResults)는 고른 값을 그대로 둔다
  const navigationType = useNavigationType()
  useEffect(() => {
    if (navigationType === 'PUSH' && !fromResults) useConditionStore.getState().setAvailableMinutes(DEFAULT_AVAILABLE_MINUTES)
    // 들어올 때 한 번만 판단한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 출발지·도착지 기본값은 현재 위치. 화면에 들어왔을 때 비어 있는 쪽만 현재 위치로 채운다
  // (이미 고른 위치는 건드리지 않는다. 위치 권한이 없으면 비운 채로 두고 직접 설정 안내)
  useEffect(() => {
    const { origin: o, destination: d } = useConditionStore.getState()
    if (o && d) return
    let ignore = false
    void (async () => {
      const position = await requestCurrentPosition()
      if (ignore || !position) return
      // [현재 위치 사용]을 눌렀을 때와 같은 값 (입력란에 '현재 위치 : 건물·주소')
      const current = await toCurrentLocation(position.lat, position.lng)
      if (ignore) return
      const state = useConditionStore.getState()
      if (!state.origin) state.setOrigin(current)
      if (!state.destination) state.setDestination(current)
    })()
    return () => {
      ignore = true
    }
  }, [])

  // 출발지·도착지가 없으면 비활성 (이용 시간은 슬라이더가 1시간 아래로 내려가지 않는다). 카테고리는 비어 있으면 랜덤으로 진행
  // 기준은 추천 API 검증 규칙과 같은 canRequestRecommend (recommendRequest.ts)
  const canSubmit = canRequestRecommend({ origin, destination, availableMinutes, startTime })

  return (
    <>
      <div className="flex flex-col gap-6 px-4 pt-3 pb-6">
        <Section title="위치">
          <LocationFields />
        </Section>

        <Section title="여유 시간" badge={formatMinutes(availableMinutes)}>
          <TimeSelector />
        </Section>

        <Section title="이동수단">
          <SegmentedControl
            options={TRANSPORT_OPTIONS}
            value={transportType}
            onChange={setTransportType}
            stickers={TRANSPORT_STICKER}
          />
        </Section>

        <Section
          title="관심 카테고리"
          count={categoryCount > 0 ? `${categoryCount} / ${MAX_CATEGORIES}개 골랐어요` : undefined}
        >
          <CategorySelector />
        </Section>
      </div>

      {/* 목업: 핑크 [추천받기] 양옆에서 토끼·거북이가 빼꼼 */}
      <BottomActionBar>
        <div className="flex w-full items-end gap-1">
          <StickerImage name="rabbitHead" className="-mb-1 size-12 -rotate-6" />
          <Button
            variant="pink"
            className="flex flex-1 items-center justify-center gap-1"
            disabled={!canSubmit}
            onClick={() => {
              // 조건 → 추천 요청 → POST /api/recommendations (결과 화면이 계산 중 표시를 보여 준다)
              const request = toRecommendRequest(useConditionStore.getState())
              if (!request) return
              void requestRecommend(request)
              if (fromResults) navigate(-1)
              else navigate('/results')
            }}
          >
            추천받기
            <Icon name="chevronRight" className="size-5" strokeWidth={2.8} />
          </Button>
          <StickerImage name="turtleHead" className="-mb-1 size-12 rotate-6" />
        </div>
      </BottomActionBar>
    </>
  )
}

/** 구역 제목 (제목 폰트) + 선택 값 배지(노란 라벨) 또는 작은 안내 */
function Section({
  title,
  badge,
  hint,
  count,
  children,
}: {
  title: string
  badge?: string
  hint?: string
  /** hint 오른쪽에 붙는 진행 상황 (예: 고른 카테고리 개수) */
  count?: string
  children: ReactNode
}) {
  return (
    <section>
      <h2 className="mb-2.5 flex items-center gap-2 px-1">
        <span className="font-display text-lg">{title}</span>
        {badge && <span className="rounded-full bg-sunny px-2.5 py-0.5 text-xs font-bold">{badge}</span>}
        {hint && <span className="text-xs text-neutral-400">{hint}</span>}
        {count && <span className="text-xs font-bold text-primary-dark">{count}</span>}
      </h2>
      {children}
    </section>
  )
}
