import { useState } from 'react'
import { useNavigate } from 'react-router'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import EmptyState from '../../../shared/components/EmptyState'
import LoadingState from '../../../shared/components/LoadingState'
import StickerImage from '../../../shared/components/StickerImage'
import { useSavedCourses } from '../hooks/useSavedCourses'
import SavedCourseCard from './SavedCourseCard'

// MY-02: 서버의 요약 목록을 표시하고, 확인 후 삭제 API를 호출한다.
// 디자인: 별 스티커 머리말 + 추천 결과와 같은 종이 카드(핑크 띠·테이프) 목록
export default function SavedCourseList() {
  const navigate = useNavigate()
  const { data, loading, error, deletingId, deleteError, loadMore, retry, removeCourse, clearDeleteError } = useSavedCourses()
  const [removingId, setRemovingId] = useState<string | null>(null)

  async function handleRemove() {
    if (!removingId) return
    if (await removeCourse(removingId)) setRemovingId(null)
  }

  if (!data && loading) return <LoadingState message="저장한 코스를 불러오고 있어요" />

  return (
    <div aria-busy={loading || deletingId !== null}>
      {/* 머리말: 별 스티커 + 모아 둔 개수 */}
      {data && data.totalElements > 0 && (
        <div className="flex items-center gap-3 px-5 pt-5 pb-1">
          <StickerImage name="navSaved" className="size-11 -rotate-6" />
          <p className="font-display text-xl leading-tight">
            모아 둔 코스 <span className="text-primary-dark">{data.totalElements}</span>개
          </p>
        </div>
      )}

      {/* 조회에 실패해도 이미 가져온 목록은 계속 보여 준다. */}
      {error && (
        <div role="alert" className="paper-card m-4 p-4">
          <p className="mb-3 text-sm">{error}</p>
          <Button variant="secondary" onClick={retry} disabled={loading || deletingId !== null}>다시 시도</Button>
        </div>
      )}

      {/* 현재 배열 길이 대신 서버의 전체 건수로 빈 목록을 판단한다. */}
      {data?.totalElements === 0 && !loading && !error && (
        <EmptyState
          sticker="sceneRelax"
          message="아직 저장한 코스가 없어요"
          description="마음에 드는 코스를 저장해 두면 여기 모여요"
          action={<Button className="w-auto px-6" onClick={() => navigate('/condition')}>지금 외출 추천</Button>}
        />
      )}

      {data && data.content.length > 0 && (
        <ul className="flex flex-col gap-5 p-4 pt-5">
          {data.content.map((course, i) => (
            <SavedCourseCard
              key={course.courseId}
              index={i}
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
            message="저장한 코스를 삭제할까요?"
            description={deleteError ?? undefined}
            confirmLabel={deletingId ? '삭제 중…' : '삭제'}
            onCancel={() => { if (!deletingId) setRemovingId(null) }}
            onConfirm={() => { void handleRemove() }}
          />
        </fieldset>
      )}
    </div>
  )
}
