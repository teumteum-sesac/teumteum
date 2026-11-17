import { useMemo } from 'react'
import { useSearchParams } from 'react-router'
import EmptyState from '../../../shared/components/EmptyState'
import SavedCourseMap from '../../mypage/components/SavedCourseMap'
import { useCourse } from '../../recommend/store'
import { mockRouteLegs } from '../mock'
import { getRouteStops } from '../routePath'
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

function RecommendedCourseMapView({ courseId }: { courseId: string }) {
  const course = useCourse(courseId)

  // [임시] 경로선 확인용: 개발 서버에서 ?mockRoute 를 붙였을 때만 목업 routeLegs 를 넘긴다. 운영 빌드에서는 항상 undefined
  // TODO(김용현·유지수): DET-02 API 연결 시 추천 상세·저장 코스 응답의 routeLegs 를 넘기고 이 목업 분기는 삭제
  const [searchParams] = useSearchParams()
  const mockRoute = import.meta.env.DEV && searchParams.has('mockRoute')
  const routeLegs = useMemo(() => (mockRoute && course ? mockRouteLegs(getRouteStops(course)) : undefined), [mockRoute, course])

  if (!course) return <EmptyState message="코스 정보를 찾을 수 없습니다." />
  return (
    <CourseMap
      course={course}
      backLabel="코스 상세로 돌아가기"
      routeLegs={routeLegs}
    />
  )
}
