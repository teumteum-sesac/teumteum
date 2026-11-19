import MyPageContent from '../features/mypage/components/MyPageContent'
import AppHeader from '../shared/components/AppHeader'
import BottomNav from '../shared/layout/BottomNav'

/** MY-01 마이페이지. 하단바 탭 화면이라 하단바를 두고, 홈은 하단바에서 간다 */
export default function MyPage() {
  return (
    <>
      <AppHeader title="마이페이지" showHome={false} />
      <MyPageContent />
      <BottomNav />
    </>
  )
}
