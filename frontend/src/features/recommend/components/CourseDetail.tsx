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
import { formatMinutes } from '../../../shared/utils/format'
import { deleteCourse, saveCourse } from '../../mypage/api/courseApi'
import { useRecommendCourse } from '../hooks/useRecommendCourse'
import { isExpiredError } from '../store'
import CourseSchedule from './CourseSchedule'
import CourseSummary, { Pill } from './CourseSummary'
import ExpiredState from './ExpiredState'

/**
 * 추천 결과 상세 (DET-01)
 * - GET /api/recommendations/courses/{recommendationId} 로 일정·경로를 불러온다 (새로고침·주소 직접 진입도 동작)
 * - 장소 교체(DET-03) 뒤에는 새 recommendationId 주소로 들어오므로 바뀐 코스가 조회된다
 * - 2시간 지남(410)·없음(404) → 만료 안내
 * - 서버 저장 응답이 확인된 경우에만 저장됨을 표시한다. 저장됨 상태에서 버튼을 한 번 더 누르면 저장을 취소한다(DELETE)
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
  // 저장·저장 취소 결과 토스트
  const [toast, setToast] = useState<'saved' | 'unsaved' | null>(null)
  const closeToast = useCallback(() => setToast(null), [])
  // 저장 취소 요청 중
  const [unsaving, setUnsaving] = useState(false)
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
      navigate('/login', { state, replace: true }) // 로그인 후 이 화면으로 돌아오므로 기록에 두 번 남지 않게
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
      setToast('saved')
    } catch (e) {
      if (!active.current) return
      const currentToken = useAuthStore.getState().accessToken
      if (currentToken && currentToken !== requestToken) return
      if (e instanceof ApiError && e.status === 401) {
        const state: LoginRedirectState = { from: location.pathname, pendingAction: 'save' }
        navigate('/login', { state, replace: true }) // 로그인 후 이 화면으로 돌아오므로 기록에 두 번 남지 않게
      } else {
        setSaveError(e instanceof ApiError ? e : new ApiError('UNKNOWN', '코스를 저장하지 못했어요. 다시 시도해 주세요.', 0))
      }
    } finally {
      savingRef.current = false
      if (active.current) setSaving(false)
    }
  }, [course, isLoggedIn, navigate, location.pathname])

  // 저장된 상태에서 한 번 더 누르면 저장 취소 (방금 저장한 코스를 DELETE /api/courses/{courseId})
  const handleUnsave = useCallback(async () => {
    if (!savedResult || savingRef.current) return
    savingRef.current = true
    setUnsaving(true)
    setSaveError(null)
    try {
      await deleteCourse(savedResult.courseId)
      if (!active.current) return
      setSavedResult(null)
      setToast('unsaved')
    } catch (e) {
      if (!active.current) return
      // 이미 지워진 코스(404)면 저장 안 된 상태로 맞춘다
      if (e instanceof ApiError && e.status === 404) {
        setSavedResult(null)
        setToast('unsaved')
      } else {
        setSaveError(e instanceof ApiError ? e : new ApiError('UNKNOWN', '저장을 취소하지 못했어요. 다시 시도해 주세요.', 0))
      }
    } finally {
      savingRef.current = false
      if (active.current) setUnsaving(false)
    }
  }, [savedResult])

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
      {/* 상단: 듀오 장면 배너 + 코스명 라벨만 (요약 알약은 타임라인 아래로 옮김) */}
      <CourseSummary course={course} pills={false} />

      <section className="px-4 pt-2 pb-6">
        <h3 className="px-1 font-display text-lg">코스 타임라인</h3>
        {/* 코스 요약은 여기 알약 두 개만 (총 시간 · 장소 수). 이동시간 API 실패로 직선거리 계산이면 '예상 시간' 표시 */}
        {/* 높이 h-8 · 아래 여백 mb-2: 오른쪽에 겹쳐 오는 [카카오맵 길찾기](CourseDirectionsSidebar, -mt-10)와 같은 줄에 맞춘다 */}
        <div className="mt-2 mb-2 flex h-8 items-center gap-1.5 px-1">
          <Pill icon="clock" strong>
            총 {formatMinutes(course.totalMinutes)}
          </Pill>
          <Pill icon="location">{course.places.length}곳</Pill>
          {course.estimated && <span className="rounded-full bg-sunny px-2.5 py-1 text-xs font-bold">예상 시간</span>}
        </div>
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
          // state: 지도 화면이 "상세에서 왔다" 는 걸 알고 [코스 상세 보기] 를 뒤로 가기로 처리하게 한다
          onClick={() => navigate(`/results/${course.courseId}/map`, { state: { from: 'detail' } })}
        >
          <Icon name="map" className="size-5" />
          지도
        </Button>
        {/* 저장 전: [이 코스 저장하기] → 저장 후: [저장됨 · 저장 취소] (한 번 더 누르면 저장 취소) */}
        <Button
          variant={saved ? 'secondary' : 'pink'}
          className="flex items-center justify-center gap-1.5"
          aria-pressed={saved}
          onClick={() => { void (saved ? handleUnsave() : handleSave()) }}
          disabled={saving || unsaving}
        >
          <Icon name={saved ? 'check' : 'bookmark'} className="size-5" strokeWidth={2.6} />
          {saving ? '저장 중…' : unsaving ? '취소 중…' : saved ? '저장됨 · 저장 취소' : '이 코스 저장하기'}
        </Button>
      </BottomActionBar>

      {toast === 'saved' && savedResult && saved && (
        <Toast message="코스를 저장했어요" action={{ label: '저장한 코스 보기', to: `/mypage/courses/${savedResult.courseId}` }} onClose={closeToast} />
      )}
      {toast === 'unsaved' && <Toast message="저장을 취소했어요" onClose={closeToast} />}
      {warning && <Toast message="선택한 시간을 넘어요" onClose={closeWarning} />}
    </>
  )
}
