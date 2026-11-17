import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import Toast from '../../../shared/components/Toast'
import { formatShortDate } from '../../../shared/utils/format'
import CourseSchedule from '../../recommend/components/CourseSchedule'
import CourseSummary from '../../recommend/components/CourseSummary'
import { useSavedCourseStore } from '../store'

/**
 * 저장한 코스 상세 (MY-03): DET-01과 같은 구성, 저장 시점 정보로 표시 (장소 교체 없음).
 * '정보 새로고침'을 누를 때만 외부 API를 다시 조회한다.
 * TODO(김하윤): 정보 새로고침 API 연동
 */
export default function SavedCourseDetail({ courseId }: { courseId: string }) {
  const navigate = useNavigate()
  const { savedCourses, remove } = useSavedCourseStore()
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [toast, setToast] = useState(false)
  const closeToast = useCallback(() => setToast(false), [])
  const saved = savedCourses.find((s) => s.course.courseId === courseId)

  if (!saved) return <EmptyState message="저장한 코스를 찾을 수 없습니다." />

  return (
    <>
      <CourseSummary course={saved.course} meta={`${formatShortDate(saved.savedAt)} 저장`}>
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
        <CourseSchedule course={saved.course} />
      </section>

      <BottomActionBar>
        <Button variant="secondary" className="flex w-auto shrink-0 items-center justify-center gap-1 px-4" onClick={() => setConfirmRemove(true)}>
          <Icon name="heart" className="size-5 fill-primary text-primary" />
          해제
        </Button>
        <Button className="flex items-center justify-center gap-1.5" onClick={() => navigate(`/mypage/courses/${courseId}/map`)}>
          <Icon name="map" className="size-5" />
          지도에서 보기
        </Button>
      </BottomActionBar>

      {confirmRemove && (
        <ConfirmDialog
          message="저장을 해제할까요?"
          confirmLabel="저장 해제"
          onCancel={() => setConfirmRemove(false)}
          onConfirm={() => {
            remove(courseId)
            navigate('/mypage/courses', { replace: true })
          }}
        />
      )}
      {toast && <Toast message="정보 새로고침은 API 연동 후 동작합니다" onClose={closeToast} />}
    </>
  )
}
