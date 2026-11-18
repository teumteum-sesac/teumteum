import AdminScreen from '../features/admin/components/AdminScreen'
import AppHeader from '../shared/components/AppHeader'

/** 관리자 화면 /admin (회원 관리·공지사항). 로그인은 router 의 RequireAuth, 관리자 여부는 AdminScreen 이 확인 */
export default function AdminPage() {
  return (
    <>
      <AppHeader title="관리자" />
      <AdminScreen />
    </>
  )
}
