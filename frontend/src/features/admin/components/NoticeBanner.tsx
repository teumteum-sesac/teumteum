import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import StickerImage from '../../../shared/components/StickerImage'
import { getNotices } from '../api/adminApi'
import type { NoticeResponse } from '../types'

/*
 * [이 파일이 하는 일]
 * 홈 상단 공지 배너 = 전체 알림 (REQ-053 대체). 가장 최근 공지 1건을 보여 주고, 누르면 공지 상세로.
 * [닫기] 를 누르면 그 공지는 이 브라우저에서 다시 띄우지 않는다 (공지 번호를 localStorage 에 기억).
 * 공지가 없거나 불러오기 실패면 아무것도 그리지 않는다 (홈 사용을 방해하지 않게).
 */
const DISMISSED_KEY = 'teumteum-dismissed-notice'

function readDismissed(): number | null {
  try {
    const v = localStorage.getItem(DISMISSED_KEY)
    return v ? Number(v) : null
  } catch {
    return null
  }
}

export default function NoticeBanner() {
  const [notice, setNotice] = useState<NoticeResponse | null>(null)
  const [dismissed, setDismissed] = useState<number | null>(readDismissed)

  useEffect(() => {
    let ignore = false
    getNotices(0, 1)
      .then((res) => {
        if (!ignore) setNotice(res.content[0] ?? null)
      })
      .catch(() => {
        // 배너는 부가 기능이라 실패해도 안내하지 않는다
      })
    return () => {
      ignore = true
    }
  }, [])

  if (!notice || dismissed === notice.noticeId) return null

  const dismiss = () => {
    setDismissed(notice.noticeId)
    try {
      localStorage.setItem(DISMISSED_KEY, String(notice.noticeId))
    } catch {
      // 저장이 막힌 브라우저면 이번 화면에서만 숨긴다
    }
  }

  return (
    <div className="paper-card flex items-center gap-2 py-1.5 pr-1.5 pl-2.5">
      <StickerImage name="decoStar" className="size-6" />
      <Link to={`/notices/${notice.noticeId}`} className="min-w-0 flex-1 truncate text-sm font-bold">
        {notice.title}
      </Link>
      <button type="button" onClick={dismiss} aria-label="공지 닫기" className="flex size-7 items-center justify-center rounded-full text-neutral-400">
        ✕
      </button>
    </div>
  )
}
