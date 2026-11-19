import { useLocation } from 'react-router'
import EmptyState from '../../../shared/components/EmptyState'
import LoadingState from '../../../shared/components/LoadingState'
import SavedCourseMap from '../../mypage/components/SavedCourseMap'
import ExpiredState from '../../recommend/components/ExpiredState'
import { useRecommendCourse } from '../../recommend/hooks/useRecommendCourse'
import { isExpiredError } from '../../recommend/store'
import CourseMap from './CourseMap'

interface CourseMapViewProps {
  courseId: string
  /** recommend: 추천 결과(RES-01)·추천 상세(DET-01)에서 진입, saved: 저장한 코스(MY-03)에서 진입 */
  source: 'recommend' | 'saved'
}

/** DET-02 진입 경로에 따라 코스를 찾아 지도에 표시한다 */
export default function CourseMapView({ courseId, source }: CourseMapViewProps) {
  return source === 'saved' ? <SavedCourseMap courseId={courseId} /> : <RecommendedCourseMapView courseId={courseId} />
}

/**
 * 추천 코스 지도: GET /api/recommendations/courses/{id} 의 routeLegs 로 경로선을 그린다
 * (ACTUAL 구간은 실제 길, STRAIGHT·없음은 직선 — routePath.ts). 2시간 지나면 만료 안내
 * <p>
 * 화면 순서는 추천 결과 → 지도 → 상세 (#177). 하단 버튼은 들어온 곳에 따라 다르다.
 * - 추천 결과에서 옴: [코스 상세 보기] → 상세로 이동. 상세에서 뒤로 가면 다시 지도, 한 번 더 뒤로 가면 추천 결과
 * - 상세의 [지도] 에서 옴: [코스 상세 보기] → 뒤로 가기 (문구는 추천 결과에서 올 때와 같게). 상세로 "이동" 하면 상세·지도가 기록에 계속 쌓여서
 *   뒤로 가기를 여러 번 눌러야 추천 결과로 돌아가게 된다
 */
function RecommendedCourseMapView({ courseId }: { courseId: string }) {
  const { course, routeLegs, loading, error } = useRecommendCourse(courseId)
  const location = useLocation()
  const fromDetail = (location.state as { from?: string } | null)?.from === 'detail'

  if (loading) return <LoadingState message="지도를 준비하고 있어요" />
  if (error && isExpiredError(error.code)) return <ExpiredState />
  if (!course) return <EmptyState message="코스 정보를 찾을 수 없어요." description={error?.message} />
  return fromDetail ? (
    <CourseMap course={course} backLabel="코스 상세 보기" routeLegs={routeLegs} />
  ) : (
    <CourseMap course={course} backLabel="코스 상세 보기" backTo={`/results/${courseId}`} routeLegs={routeLegs} />
  )
}
