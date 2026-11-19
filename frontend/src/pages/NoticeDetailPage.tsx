import { useParams } from 'react-router'
import NoticeDetail from '../features/admin/components/NoticeDetail'
import AppHeader from '../shared/components/AppHeader'

/** 공지 상세 /notices/:noticeId (누구나, 홈 배너에서 진입) */
export default function NoticeDetailPage() {
  const { noticeId = '' } = useParams()
  return (
    <>
      <AppHeader title="공지사항" />
      <NoticeDetail noticeId={Number(noticeId)} />
    </>
  )
}
