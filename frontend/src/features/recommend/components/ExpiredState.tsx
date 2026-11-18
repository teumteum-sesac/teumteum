import { useNavigate } from 'react-router'
import Button from '../../../shared/components/Button'
import EmptyState from '../../../shared/components/EmptyState'
import type { RecommendRequest } from '../../condition/recommendRequest'
import { useRecommendStore } from '../store'

/**
 * 추천 결과가 2시간이 지났거나(410) 없어졌을 때(404) (화면설계서 공통 정책: 추천 만료, REQ-056)
 * [다시 추천] = 마지막 추천에 쓴 조건(result.condition)으로 새로 추천한다. 조건이 없으면 외출 조건 화면으로.
 */
export default function ExpiredState() {
  const navigate = useNavigate()
  const condition = useRecommendStore((s) => s.result?.condition)
  const request = useRecommendStore((s) => s.request)

  const retry = () => {
    if (!condition) {
      navigate('/condition')
      return
    }
    // 응답의 조건(RecommendConditionResponse)은 요청(RecommendRequest)과 같은 모양이다
    const body: RecommendRequest = {
      origin: condition.origin,
      destination: condition.destination,
      startTime: condition.startTime,
      availableMinutes: condition.availableMinutes,
      transportType: condition.transportType,
      categoryMode: condition.categoryMode,
      categories: condition.categories,
    }
    void request(body)
    navigate('/results', { replace: true })
  }

  return (
    <EmptyState
      sticker="sceneResting"
      message="추천 보관 시간이 지났어요"
      description="둘이 쉬는 사이 2시간이 흘렀네요. 같은 조건으로 다시 찾아 드릴게요."
      action={
        <Button className="mx-auto w-auto px-8" onClick={retry}>
          다시 추천
        </Button>
      }
    />
  )
}
