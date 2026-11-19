import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useGoBack } from '../../../shared/hooks/useGoBack'
import { ApiError } from '../../../shared/api/apiError'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import Icon from '../../../shared/components/Icon'
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
 * - 삭제: DELETE /api/courses/{courseId} 성공 후 목록으로
 * - 장소의 최신 정보(영업시간·후기 등)는 타임라인의 [장소 상세] → 카카오맵 칸에서 본다 (정보 새로고침 없음)
 */
export default function SavedCourseDetail({ courseId }: { courseId: string }) {
  // 코스 ID 가 바뀌면 화면 상태(삭제 중·오류 등)를 처음부터 다시 시작한다
  return <SavedCourseDetailContent key={courseId} courseId={courseId} />
}

function SavedCourseDetailContent({ courseId }: { courseId: string }) {
  const navigate = useNavigate()
  // 삭제 후 → 들어온 목록으로 돌아간다 (목록을 새로 열면 같은 화면이 기록에 두 번 남는다)
  const backToList = useGoBack('/mypage/courses')
  const { data, loading, error, retry } = useSavedCourse(courseId)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  // 두 번 눌러 삭제 요청이 겹치지 않게, 화면을 떠난 뒤 온 응답은 무시하도록
  const deletingRef = useRef(false)
  const active = useRef(true)

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
      backToList()
    } catch (e) {
      if (active.current) setDeleteError(e instanceof ApiError ? e.message : '코스를 삭제하지 못했어요.')
    } finally {
      deletingRef.current = false
      if (active.current) setDeleting(false)
    }
  }

  if (!data) return <SavedCourseRequestState loading={loading} error={error} onRetry={retry} />
  const course = toSavedCourse(data)

  return (
    <>
      <CourseSummary course={course} title={data.courseName} meta={`${formatShortDate(data.createdAt.slice(0, 10))} 저장`} />

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
          <Icon name="close" className="size-5" />
          삭제
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
            message="저장한 코스를 삭제할까요?"
            description={deleteError ?? undefined}
            confirmLabel={deleting ? '삭제 중…' : '삭제'}
            onCancel={() => {
              if (!deleting) setConfirmRemove(false)
            }}
            onConfirm={() => {
              void handleDelete()
            }}
          />
        </fieldset>
      )}
    </>
  )
}
