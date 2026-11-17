import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import { ApiError } from '../../../shared/api/apiError'
import Toast from '../../../shared/components/Toast'
import { formatMinutes, formatShortDate } from '../../../shared/utils/format'
import CourseSchedule from '../../recommend/components/CourseSchedule'
import { deleteCourse } from '../api/courseApi'
import { toSavedCourse } from '../courseAdapter'
import { useSavedCourse } from '../hooks/useSavedCourse'
import SavedCourseRequestState from './SavedCourseRequestState'

/**
 * 저장한 코스 상세 (MY-03): DET-01과 같은 구성, 저장 시점 정보로 표시 (장소 교체 없음).
 * '정보 새로고침'을 누를 때만 외부 API를 다시 조회한다.
 * 최신 정보 조회 API는 아직 준비 중이며 저장본을 수정하지 않는다.
 */
export default function SavedCourseDetail({ courseId }: { courseId: string }) {
  return <SavedCourseDetailContent key={courseId} courseId={courseId} />
}

function SavedCourseDetailContent({ courseId }: { courseId: string }) {
  const navigate = useNavigate()
  const { data, loading, error, retry } = useSavedCourse(courseId)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const deletingRef = useRef(false)
  const active = useRef(true)
  const [toast, setToast] = useState(false)
  const closeToast = useCallback(() => setToast(false), [])
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])
  async function handleDelete() {
    if (deletingRef.current) return
    deletingRef.current = true
    setDeleting(true)
    setDeleteError(null)
    try {
      const response = await deleteCourse(courseId)
      if (!active.current) return
      if (!response.deleted || response.courseId !== courseId) throw new Error('Unexpected delete response')
      navigate('/mypage/courses', { replace: true })
    } catch (e) {
      if (active.current) setDeleteError(e instanceof ApiError ? e.message : '저장을 해제하지 못했어요.')
    } finally {
      deletingRef.current = false
      if (active.current) setDeleting(false)
    }
  }

  if (!data) return <SavedCourseRequestState loading={loading} error={error} onRetry={retry} />
  const course = toSavedCourse(data)

  return (
    <>
      {/* 코스명은 장소명을 조합하지 않고 저장 당시 courseName을 그대로 표시한다. */}
      <section className="border-b border-line px-4 py-5">
        <h2 className="text-lg font-bold">{data.courseName}</h2>
        <p className="mt-2 text-sm text-neutral-600">
          총 {formatMinutes(data.totalMinutes)} · 이동 {formatMinutes(data.totalTravelMinutes)} · {data.places.length}곳 · {formatShortDate(data.createdAt.slice(0, 10))} 저장
          {data.estimated && <span className="ml-1.5 text-xs font-semibold text-amber-600">예상 시간</span>}
        </p>
        <p className="mt-2 text-xs text-neutral-500">
          저장 시점 정보예요 ·{' '}
          <button type="button" onClick={() => setToast(true)} className="text-primary">
            정보 새로고침 ↻
          </button>
        </p>
      </section>

      <section className="px-4 py-5">
        <h3 className="mb-4 text-sm font-bold">코스 일정</h3>
        <CourseSchedule course={course} />
      </section>

      <BottomActionBar>
        <Button variant="secondary" disabled={deleting} onClick={() => {
          setDeleteError(null)
          setConfirmRemove(true)
        }}>
          저장 해제
        </Button>
        <Button disabled={deleting} onClick={() => navigate(`/mypage/courses/${courseId}/map`)}>지도에서 보기</Button>
      </BottomActionBar>

      {confirmRemove && (
        <fieldset disabled={deleting} className="contents">
          <ConfirmDialog
          message="저장을 해제할까요?"
          description={deleteError ?? undefined}
          confirmLabel={deleting ? '저장 해제 중…' : '저장 해제'}
          onCancel={() => { if (!deleting) setConfirmRemove(false) }}
          onConfirm={() => { void handleDelete() }}
          />
        </fieldset>
      )}
      {toast && <Toast message="정보 새로고침은 API 연동 후 동작합니다" onClose={closeToast} />}
    </>
  )
}
