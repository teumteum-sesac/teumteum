import { useState } from 'react'
import { useNavigate } from 'react-router'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import EmptyState from '../../../shared/components/EmptyState'
import { useSavedCourses } from '../hooks/useSavedCourses'
import SavedCourseCard from './SavedCourseCard'

// MY-02: 서버의 요약 목록을 표시하고, 확인 후 삭제 API를 호출한다.
export default function SavedCourseList() {
  const navigate = useNavigate()
  const { data, loading, error, deletingId, deleteError, loadMore, retry, removeCourse, clearDeleteError } = useSavedCourses()
  const [removingId, setRemovingId] = useState<string | null>(null)

  async function handleRemove() {
    if (!removingId) return
    if (await removeCourse(removingId)) setRemovingId(null)
  }

  if (!data && loading) {
    return <p role="status" className="p-6 text-center text-sm text-neutral-500">저장한 코스를 불러오는 중이에요.</p>
  }

  return (
    <div aria-busy={loading || deletingId !== null}>
      {data && <p className="px-4 pt-4 text-sm text-neutral-500">저장한 코스 {data.totalElements}개</p>}

      {/* 조회에 실패해도 이미 가져온 목록은 계속 보여 준다. */}
      {error && (
        <div role="alert" className="m-4 rounded-xl border border-line p-4">
          <p className="mb-3 text-sm">{error}</p>
          <Button variant="secondary" onClick={retry} disabled={loading || deletingId !== null}>다시 시도</Button>
        </div>
      )}

      {/* 현재 배열 길이 대신 서버의 전체 건수로 빈 목록을 판단한다. */}
      {data?.totalElements === 0 && !loading && !error && (
        <EmptyState
          message="아직 저장한 코스가 없어요"
          action={<Button className="w-auto px-6" onClick={() => navigate('/condition')}>지금 외출 추천</Button>}
        />
      )}

      {data && data.content.length > 0 && (
        <ul className="flex flex-col gap-3 p-4">
          {data.content.map((course) => (
            <SavedCourseCard
              key={course.courseId}
              course={course}
              disabled={loading || deletingId !== null}
              onRemove={() => {
                clearDeleteError()
                setRemovingId(course.courseId)
              }}
            />
          ))}
        </ul>
      )}

      {data && loading && <p role="status" className="p-4 text-center text-sm text-neutral-500">목록을 불러오는 중이에요.</p>}
      {data?.hasNext && !error && (
        <div className="px-4 pb-4">
          <Button variant="secondary" onClick={loadMore} disabled={loading || deletingId !== null}>더 보기</Button>
        </div>
      )}

      {removingId && (
        // 공통 Dialog를 변경하지 않고 삭제 요청 동안 내부 버튼들을 비활성화한다.
        <fieldset disabled={deletingId !== null} className="contents">
          <ConfirmDialog
            message="저장을 해제할까요?"
            description={deleteError ?? undefined}
            confirmLabel={deletingId ? '저장 해제 중…' : '저장 해제'}
            onCancel={() => { if (!deletingId) setRemovingId(null) }}
            onConfirm={() => { void handleRemove() }}
          />
        </fieldset>
      )}
    </div>
  )
}
