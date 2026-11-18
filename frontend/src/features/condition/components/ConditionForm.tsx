import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import { TRANSPORT_STICKER } from '../../../shared/assets/stickerAssets'
import Button from '../../../shared/components/Button'
import Icon from '../../../shared/components/Icon'
import SegmentedControl from '../../../shared/components/SegmentedControl'
import StickerImage from '../../../shared/components/StickerImage'
import { MAX_CATEGORIES, TRANSPORT_OPTIONS } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { useRecommendStore } from '../../recommend/store'
import { canRequestRecommend } from '../recommendRequest'
import { useConditionStore } from '../store'
import CategorySelector from './CategorySelector'
import LocationFields from './LocationFields'
import TimeSelector from './TimeSelector'

/** 외출 조건 설정 (CON-01): 위치 → 시간 → 이동수단 → 카테고리 → 추천받기 */
export default function ConditionForm() {
  const navigate = useNavigate()
  const { origin, availableMinutes, startTime, transportType, setTransportType } = useConditionStore()
  const requestRecommend = useRecommendStore((s) => s.request)

  // 출발지가 없거나 이용시간이 0분이면 비활성 (REQ-008, 0분은 슬라이더 시작 위치). 카테고리는 비어 있으면 랜덤으로 진행
  // 기준은 추천 API 검증 규칙과 같은 canRequestRecommend (recommendRequest.ts)
  // TODO(나혜빈): RES-01 연결 시 requestRecommend(목업) 대신 toRecommendRequest(조건) 으로 POST /api/recommendations 호출
  const canSubmit = canRequestRecommend({ origin, availableMinutes, startTime })

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

        <Section title="관심 카테고리" hint={`최대 ${MAX_CATEGORIES}개`}>
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
              requestRecommend()
              navigate('/results')
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
function Section({ title, badge, hint, children }: { title: string; badge?: string; hint?: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="mb-2.5 flex items-center gap-2 px-1">
        <span className="font-display text-lg">{title}</span>
        {badge && <span className="rounded-full bg-sunny px-2.5 py-0.5 text-xs font-bold">{badge}</span>}
        {hint && <span className="text-xs text-neutral-400">{hint}</span>}
      </h2>
      {children}
    </section>
  )
}
