import { Link } from 'react-router'
import Button from '../../../shared/components/Button'
import EmptyState from '../../../shared/components/EmptyState'
import LoadingState from '../../../shared/components/LoadingState'

// 저장 상세(MY-03)와 저장 코스 지도(DET-02)에서 쓰는 조회 중·실패 안내다.
export default function SavedCourseRequestState({ loading, error, onRetry }: {
  loading: boolean
  error: string | null
  onRetry: () => void
}) {
  if (loading) return <LoadingState message="코스를 불러오는 중이에요" />
  return (
    <div role="alert">
      <EmptyState
        sticker="sceneEmpty"
        message={error ?? '코스 정보를 찾을 수 없어요.'}
        action={
          <>
            <Button className="mx-auto w-auto px-8" onClick={onRetry}>
              다시 시도
            </Button>
            <Link to="/mypage/courses" className="mt-1 text-sm text-neutral-500 underline underline-offset-4">
              저장한 코스 목록으로
            </Link>
          </>
        }
      />
    </div>
  )
}
