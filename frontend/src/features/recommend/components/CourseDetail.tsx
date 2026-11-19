import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../shared/api/apiError'
import { useLocation, useNavigate } from 'react-router'
import { useAuthStore } from '../../../shared/auth/authStore'
import type { LoginRedirectState } from '../../../shared/auth/RequireAuth'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import LoadingState from '../../../shared/components/LoadingState'
import Toast from '../../../shared/components/Toast'
import { saveCourse } from '../../mypage/api/courseApi'
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
 * - 서버 저장 응답이 확인된 경우에만 저장됨을 표시한다
 */
export default function CourseDetail({ courseId }: { courseId: string }) {
  return <CourseDetailContent key={courseId} courseId={courseId} />
}

function CourseDetailContent({ courseId }: { courseId: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const token = useAuthStore((s) => s.accessToken)
  const [savedResult, setSavedResult] = useState<{ token: string; courseId: string } | null>(null)
  const saved = savedResult !== null && savedResult.token === token
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<ApiError | null>(null)
  const savingRef = useRef(false)
  const active = useRef(true)
  const resuming = useRef(false)
  // location.key: 화면에 들어올 때마다 바뀌는 값. 교체 후 돌아오면 다시 조회하게 한다
  const { course, loading, error } = useRecommendCourse(courseId, location.key)

  const routeState = location.state as { afterLogin?: string; replaceWarning?: boolean } | null
  // 비로그인 저장 → 로그인 → 이 화면으로 돌아오면 저장을 이어서 진행하고 토스트 표시
  const pendingSave = routeState?.afterLogin === 'save'
  const [toast, setToast] = useState(false)
  const closeToast = useCallback(() => setToast(false), [])
  // 장소 교체(DET-03) 후 이용 시간을 넘었으면 경고
  const [warning, setWarning] = useState(Boolean(routeState?.replaceWarning))
  const closeWarning = useCallback(() => setWarning(false), [])

  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])

  const handleSave = useCallback(async () => {
    if (!course || savingRef.current) return
    // 장소 단독 상세는 아직 추천 버전 ID가 없어 서버 코스 저장 대상으로 전송하지 않는다.
    if (course.courseId.startsWith('place-')) {
      setSaveError(new ApiError('RECOMMENDATION_REQUIRED', '이 장소가 포함된 코스를 추천받은 후 저장해 주세요.', 0))
      return
    }
    if (!isLoggedIn) {
      const state: LoginRedirectState = { from: location.pathname, pendingAction: 'save' }
      navigate('/login', { state })
      return
    }
    const requestToken = useAuthStore.getState().accessToken
    if (!requestToken) return
    savingRef.current = true
    setSaving(true)
    setSaveError(null)
    try {
      const result = await saveCourse({ recommendationId: course.courseId })
      if (!active.current || useAuthStore.getState().accessToken !== requestToken) return
      setSavedResult({ token: requestToken, courseId: result.courseId })
      setToast(true)
    } catch (e) {
      if (!active.current) return
      const currentToken = useAuthStore.getState().accessToken
      if (currentToken && currentToken !== requestToken) return
      if (e instanceof ApiError && e.status === 401) {
        const state: LoginRedirectState = { from: location.pathname, pendingAction: 'save' }
        navigate('/login', { state })
      } else {
        setSaveError(e instanceof ApiError ? e : new ApiError('UNKNOWN', '코스를 저장하지 못했어요. 다시 시도해 주세요.', 0))
      }
    } finally {
      savingRef.current = false
      if (active.current) setSaving(false)
    }
  }, [course, isLoggedIn, navigate, location.pathname])

  useEffect(() => {
    if (pendingSave && isLoggedIn && course && !resuming.current) {
      resuming.current = true
      navigate(location.pathname, { replace: true, state: null })
      void handleSave()
    }
  }, [pendingSave, isLoggedIn, course, navigate, location.pathname, handleSave])

  if (loading) return <LoadingState message="코스를 불러오는 중이에요" />
  if ((error && isExpiredError(error.code)) || (saveError && isExpiredError(saveError.code))) return <ExpiredState />
  if (!course) return <EmptyState sticker="sceneEmpty" message="코스를 불러오지 못했어요" description={error?.message} />

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

      {saveError && <p role="alert" className="px-4 pb-4 text-sm text-red-600">{saveError.message}</p>}
      {saveError?.code === 'RECOMMENDATION_REQUIRED' && <Button variant="secondary" onClick={() => navigate('/condition')}>코스 추천받기</Button>}

      <BottomActionBar>
        <Button
          variant="secondary"
          className="flex w-auto shrink-0 items-center justify-center gap-1 px-4"
          // state: 지도 화면이 "상세에서 왔다" 는 걸 알고 [코스 상세로 돌아가기] 를 뒤로 가기로 처리하게 한다
          onClick={() => navigate(`/results/${course.courseId}/map`, { state: { from: 'detail' } })}
        >
          <Icon name="map" className="size-5" />
          지도
        </Button>
        <Button variant="pink" className="flex items-center justify-center gap-1.5" onClick={() => { void handleSave() }} disabled={saved || saving}>
          <Icon name="bookmark" className="size-5" strokeWidth={2.6} />
          {saving ? '저장 중…' : saved ? '저장됨' : '이 코스 저장하기'}
        </Button>
      </BottomActionBar>

      {toast && savedResult && saved && (
        <Toast message="저장됨" action={{ label: '저장한 코스 보기', to: `/mypage/courses/${savedResult.courseId}` }} onClose={closeToast} />
      )}
      {warning && <Toast message="선택한 시간을 넘어요" onClose={closeWarning} />}
    </>
  )
}
