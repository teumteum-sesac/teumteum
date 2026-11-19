import CourseMap from '../../map/components/CourseMap'
import { toSavedCourse } from '../courseAdapter'
import { useSavedCourse } from '../hooks/useSavedCourse'
import SavedCourseRequestState from './SavedCourseRequestState'

// 목록 화면을 거치지 않고 주소로 직접 진입해도 저장본을 서버에서 조회한다.
export default function SavedCourseMap({ courseId }: { courseId: string }) {
  const { data, loading, error, retry } = useSavedCourse(courseId)
  if (!data) return <SavedCourseRequestState loading={loading} error={error} onRetry={retry} />
  return <CourseMap key={courseId} course={toSavedCourse(data)} routeLegs={data.routeLegs} backLabel="저장한 코스로 돌아가기" backTo={`/mypage/courses/${encodeURIComponent(courseId)}`} backBy="return" />
}
