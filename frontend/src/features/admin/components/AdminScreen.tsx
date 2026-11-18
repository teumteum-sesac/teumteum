import { useState } from 'react'
import { useAuthStore } from '../../../shared/auth/authStore'
import EmptyState from '../../../shared/components/EmptyState'
import SegmentedControl from '../../../shared/components/SegmentedControl'
import AdminMembers from './AdminMembers'
import AdminNotices from './AdminNotices'

/*
 * [이 파일이 하는 일]
 * 관리자 화면 (/admin, REQ-044·045·049). 위쪽 탭으로 [회원 관리] / [공지사항] 을 바꾼다.
 * 로그인은 라우터의 RequireAuth 가 확인하고, 관리자 여부는 여기서 확인한다.
 * (화면을 막아도 실제 보호는 서버가 한다: 관리자 토큰이 아니면 /api/admin/** 는 403)
 */
type Tab = 'members' | 'notices'

const TABS: { value: Tab; label: string }[] = [
  { value: 'members', label: '회원 관리' },
  { value: 'notices', label: '공지사항' },
]

export default function AdminScreen() {
  const isAdmin = useAuthStore((s) => s.user?.admin === true)
  const [tab, setTab] = useState<Tab>('members')

  if (!isAdmin) {
    return (
      <EmptyState
        sticker="sceneEmpty"
        message="관리자만 들어올 수 있어요"
        description="관리자 계정으로 다시 로그인해 주세요. (관리자로 막 지정됐다면 로그아웃 후 다시 로그인)"
      />
    )
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      <SegmentedControl options={TABS} value={tab} onChange={setTab} />
      {tab === 'members' ? <AdminMembers /> : <AdminNotices />}
    </div>
  )
}
