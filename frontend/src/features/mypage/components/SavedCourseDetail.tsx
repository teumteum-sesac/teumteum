import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../../../shared/api/apiError'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import Icon from '../../../shared/components/Icon'
import Toast from '../../../shared/components/Toast'
import { formatShortDate } from '../../../shared/utils/format'
import CourseSchedule from '../../recommend/components/CourseSchedule'
import CourseSummary from '../../recommend/components/CourseSummary'
import { deleteCourse } from '../api/courseApi'
import { toSavedCourse } from '../courseAdapter'
import { useSavedCourse } from '../hooks/useSavedCourse'
import SavedCourseRequestState from './SavedCourseRequestState'

/**
 * 저장한 코스 상세 (MY-03): DET-01과 같은 구성, 저장 시점 정보로 표시 (장소 교체 없음).
 * - GET /api/courses/{courseId} 로 저장본을 불러온다 (주소로 직접 들어와도 서버에서 조회)
 * - 코스명은 장소명을 조합하지 않고 저장 당시 courseName 을 그대로 표시한다
 * - 저장 해제: DELETE /api/courses/{courseId} 성공 후 목록으로
 * - '정보 새로고침'(최신 정보 조회) API 는 아직 준비 중이며 저장본을 수정하지 않는다
 */
export default function SavedCourseDetail({ courseId }: { courseId: string }) {
  // 코스 ID 가 바뀌면 화면 상태(삭제 중·오류 등)를 처음부터 다시 시작한다
  return <SavedCourseDetailContent key={courseId} courseId={courseId} />
}

function SavedCourseDetailContent({ courseId }: { courseId: string }) {
  const navigate = useNavigate()
  const { data, loading, error, retry } = useSavedCourse(courseId)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  // 두 번 눌러 삭제 요청이 겹치지 않게, 화면을 떠난 뒤 온 응답은 무시하도록
  const deletingRef = useRef(false)
  const active = useRef(true)
  const [toast, setToast] = useState(false)
  const closeToast = useCallback(() => setToast(false), [])

  useEffect(() => {
    active.current = true
    return () => {
      active.current = false
    }
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
      <CourseSummary course={course} title={data.courseName} meta={`${formatShortDate(data.createdAt.slice(0, 10))} 저장`}>
        <p className="mt-3 flex items-center justify-center gap-1.5 text-xs text-neutral-500">
          저장 시점 정보예요
          <button
            type="button"
            onClick={() => setToast(true)}
            className="flex items-center gap-0.5 rounded-full border-2 border-line bg-cream px-2 py-0.5 font-bold text-primary-dark"
          >
            <Icon name="refresh" className="size-3.5" strokeWidth={2.6} />
            정보 새로고침
          </button>
        </p>
      </CourseSummary>

      <section className="px-4 pt-2 pb-6">
        <h3 className="mb-3 px-1 font-display text-lg">코스 타임라인</h3>
        <CourseSchedule course={course} />
      </section>

      <BottomActionBar>
        <Button
          variant="secondary"
          className="flex w-auto shrink-0 items-center justify-center gap-1 px-4"
          disabled={deleting}
          onClick={() => {
            setDeleteError(null)
            setConfirmRemove(true)
          }}
        >
          <Icon name="heart" className="size-5 fill-primary text-primary" />
          해제
        </Button>
        <Button
          className="flex items-center justify-center gap-1.5"
          disabled={deleting}
          onClick={() => navigate(`/mypage/courses/${courseId}/map`)}
        >
          <Icon name="map" className="size-5" />
          지도에서 보기
        </Button>
      </BottomActionBar>

      {confirmRemove && (
        // 삭제 요청 중에는 확인 창 버튼들을 막는다 (공통 ConfirmDialog 는 그대로 두고 fieldset 으로 비활성)
        <fieldset disabled={deleting} className="contents">
          <ConfirmDialog
            message="저장을 해제할까요?"
            description={deleteError ?? undefined}
            confirmLabel={deleting ? '저장 해제 중…' : '저장 해제'}
            onCancel={() => {
              if (!deleting) setConfirmRemove(false)
            }}
            onConfirm={() => {
              void handleDelete()
            }}
          />
        </fieldset>
      )}
      {toast && <Toast message="정보 새로고침은 API 연동 후 동작합니다" onClose={closeToast} />}
    </>
  )
}
