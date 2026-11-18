import EmptyState from '../../../shared/components/EmptyState'
import LoadingState from '../../../shared/components/LoadingState'
import SavedCourseMap from '../../mypage/components/SavedCourseMap'
import ExpiredState from '../../recommend/components/ExpiredState'
import { useRecommendCourse } from '../../recommend/hooks/useRecommendCourse'
import { isExpiredError } from '../../recommend/store'
import CourseMap from './CourseMap'

interface CourseMapViewProps {
  courseId: string
  /** recommend: 추천 결과(DET-01)에서 진입, saved: 저장한 코스(MY-03)에서 진입 */
  source: 'recommend' | 'saved'
}

/** DET-02 진입 경로에 따라 코스를 찾아 지도에 표시한다 */
export default function CourseMapView({ courseId, source }: CourseMapViewProps) {
  return source === 'saved' ? <SavedCourseMap courseId={courseId} /> : <RecommendedCourseMapView courseId={courseId} />
}

/**
 * 추천 코스 지도: GET /api/recommendations/courses/{id} 의 routeLegs 로 경로선을 그린다
 * (ACTUAL 구간은 실제 길, STRAIGHT·없음은 직선 — routePath.ts). 2시간 지나면 만료 안내
 */
function RecommendedCourseMapView({ courseId }: { courseId: string }) {
  const { course, routeLegs, loading, error } = useRecommendCourse(courseId)

  if (loading) return <LoadingState message="지도를 준비하고 있어요" />
  if (error && isExpiredError(error.code)) return <ExpiredState />
  if (!course) return <EmptyState message="코스 정보를 찾을 수 없어요." description={error?.message} />
  return <CourseMap course={course} backLabel="코스 상세로 돌아가기" routeLegs={routeLegs} />
}
