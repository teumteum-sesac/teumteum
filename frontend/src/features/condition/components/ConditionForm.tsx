import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import { TRANSPORT_STICKER } from '../../../shared/assets/stickerAssets'
import Button from '../../../shared/components/Button'
import SegmentedControl from '../../../shared/components/SegmentedControl'
import { MAX_CATEGORIES, TRANSPORT_OPTIONS } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { useRecommendStore } from '../../recommend/store'
import { useConditionStore } from '../store'
import CategorySelector from './CategorySelector'
import LocationFields from './LocationFields'
import TimeSelector from './TimeSelector'

/** 외출 조건 설정 (CON-01): 위치 → 시간 → 이동수단 → 카테고리 → 추천받기 */
export default function ConditionForm() {
  const navigate = useNavigate()
  const { origin, availableMinutes, transportType, setTransportType } = useConditionStore()
  const requestRecommend = useRecommendStore((s) => s.request)

  // 출발지가 없거나 이용시간이 0분이면 비활성 (REQ-008, 0분은 슬라이더 시작 위치). 카테고리는 비어 있으면 랜덤으로 진행
  const canSubmit = origin !== null && availableMinutes > 0

  return (
    <>
      <div className="flex flex-col gap-7 px-4 py-5">
        <Section title="위치">
          <LocationFields />
        </Section>

        <Section title={`시간 · ${availableMinutes === 0 ? '0분' : formatMinutes(availableMinutes)}`}>
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

        <Section title={`카테고리 (최대 ${MAX_CATEGORIES}개)`}>
          <CategorySelector />
        </Section>
      </div>

      <BottomActionBar>
        <Button
          disabled={!canSubmit}
          onClick={() => {
            requestRecommend()
            navigate('/results')
          }}
        >
          추천받기
        </Button>
      </BottomActionBar>
    </>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="mb-3 text-sm font-bold">{title}</h2>
      {children}
    </section>
  )
}
