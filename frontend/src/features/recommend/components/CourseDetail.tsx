import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useAuthStore } from '../../../shared/auth/authStore'
import type { LoginRedirectState } from '../../../shared/auth/RequireAuth'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import LoadingState from '../../../shared/components/LoadingState'
import Toast from '../../../shared/components/Toast'
import { useSavedCourseStore } from '../../mypage/store'
import { useRecommendCourse } from '../hooks/useRecommendCourse'
import { isExpiredError } from '../store'
import CourseSchedule from './CourseSchedule'
import CourseSummary from './CourseSummary'
import ExpiredState from './ExpiredState'

/**
 * 추천 결과 상세 (DET-01)
 * - GET /api/recommendations/courses/{recommendationId} 로 일정·경로를 불러온다 (새로고침·주소 직접 진입도 동작)
 * - 장소 교체(DET-03) 뒤에는 새 recommendationId 주소로 들어오므로 바뀐 코스가 조회된다
 * - 2시간 지남(410)·없음(404) → 만료 안내
 * TODO(김하윤·김용현): 코스 저장 API(POST /api/courses, #120) 나오면 연결 (현재 useSavedCourseStore 임시 저장)
 */
export default function CourseDetail({ courseId }: { courseId: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const { save, isSaved } = useSavedCourseStore()
  // location.key: 화면에 들어올 때마다 바뀌는 값. 교체 후 돌아오면 다시 조회하게 한다
  const { course, loading, error } = useRecommendCourse(courseId, location.key)

  const routeState = location.state as { afterLogin?: string; replaceWarning?: boolean } | null
  // 비로그인 저장 → 로그인 → 이 화면으로 돌아오면 저장을 이어서 진행하고 토스트 표시
  const pendingSave = routeState?.afterLogin === 'save'
  const [toast, setToast] = useState(pendingSave)
  const closeToast = useCallback(() => setToast(false), [])
  // 장소 교체(DET-03) 후 이용 시간을 넘었으면 경고
  const [warning, setWarning] = useState(Boolean(routeState?.replaceWarning))
  const closeWarning = useCallback(() => setWarning(false), [])

  useEffect(() => {
    if (pendingSave && isLoggedIn && course) {
      save(course)
      navigate(location.pathname, { replace: true, state: null })
    }
  }, [pendingSave, isLoggedIn, course, save, navigate, location.pathname])

  if (loading) return <LoadingState message="코스를 불러오는 중이에요" />
  if (error && isExpiredError(error.code)) return <ExpiredState />
  if (!course) return <EmptyState sticker="sceneEmpty" message="코스를 불러오지 못했어요" description={error?.message} />

  const saved = isSaved(course.courseId)

  const handleSave = () => {
    if (!isLoggedIn) {
      const state: LoginRedirectState = { from: location.pathname, pendingAction: 'save' }
      navigate('/login', { state })
      return
    }
    save(course)
    setToast(true)
  }

  return (
    <>
      <CourseSummary course={course} />

      <section className="px-4 pt-2 pb-6">
        <h3 className="mb-3 px-1 font-display text-lg">코스 타임라인</h3>
        <CourseSchedule
          course={course}
          onReplace={(place) => navigate(`/results/${course.courseId}/replace/${place.placeId}`)}
        />
        <p className="mt-5 text-xs text-neutral-400">이동·체류시간은 예상값이라 실제와 다를 수 있어요</p>
      </section>

      <BottomActionBar>
        <Button
          variant="secondary"
          className="flex w-auto shrink-0 items-center justify-center gap-1 px-4"
          onClick={() => navigate(`/results/${course.courseId}/map`)}
        >
          <Icon name="map" className="size-5" />
          지도
        </Button>
        <Button variant="pink" className="flex items-center justify-center gap-1.5" onClick={handleSave} disabled={saved}>
          <Icon name="bookmark" className="size-5" strokeWidth={2.6} />
          {saved ? '저장됨' : '이 코스 저장하기'}
        </Button>
      </BottomActionBar>

      {toast && (
        <Toast message="저장됨" action={{ label: '저장한 코스 보기', to: '/mypage/courses' }} onClose={closeToast} />
      )}
      {warning && <Toast message="선택한 시간을 넘어요" onClose={closeWarning} />}
    </>
  )
}
