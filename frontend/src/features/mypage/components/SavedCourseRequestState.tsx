import { Link } from 'react-router'
import Button from '../../../shared/components/Button'

// 저장 상세와 지도에서 사용하는 조회 중·실패 안내다.
export default function SavedCourseRequestState({ loading, error, onRetry }: {
  loading: boolean
  error: string | null
  onRetry: () => void
}) {
  if (loading) return <p role="status" className="p-6 text-center text-sm">코스를 불러오는 중이에요.</p>
  return (
    <div role="alert" className="p-6 text-center">
      <p className="mb-4">{error ?? '코스 정보를 찾을 수 없어요.'}</p>
      <Button onClick={onRetry}>다시 시도</Button>
      <Link to="/mypage/courses" className="mt-4 inline-block text-sm underline">저장한 코스 목록으로</Link>
    </div>
  )
}
