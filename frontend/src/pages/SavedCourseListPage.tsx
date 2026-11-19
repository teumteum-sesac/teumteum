import SavedCourseList from '../features/mypage/components/SavedCourseList'
import AppHeader from '../shared/components/AppHeader'
import BottomNav from '../shared/layout/BottomNav'

/** MY-02 저장한 코스 목록. 하단바 탭 화면이라 하단바를 두고, 홈은 하단바에서 간다 */
export default function SavedCourseListPage() {
  return (
    <>
      <AppHeader title="저장한 코스" showHome={false} backFallback="/mypage" />
      <SavedCourseList />
      <BottomNav />
    </>
  )
}
