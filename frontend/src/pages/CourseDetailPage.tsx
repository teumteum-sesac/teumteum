import { Navigate, useLocation, useParams } from 'react-router'
import CourseDetail from '../features/recommend/components/CourseDetail'
import AppHeader from '../shared/components/AppHeader'

/** DET-01 추천 결과 상세. /results/:courseId */
export default function CourseDetailPage() {
  const { courseId = '' } = useParams()
  const location = useLocation()

  // 예전 주소 /results/place-{장소ID} 는 장소 미리보기로 보낸다 (넘겨받은 장소 정보도 그대로)
  if (courseId.startsWith('place-')) {
    return <Navigate to={`/places/${courseId.slice('place-'.length)}`} replace state={location.state} />
  }

  return (
    <>
      <AppHeader title="코스 상세" />
      <CourseDetail courseId={courseId} />
    </>
  )
}
