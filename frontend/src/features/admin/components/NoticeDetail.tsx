import { useEffect, useState } from 'react'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import EmptyState from '../../../shared/components/EmptyState'
import LoadingState from '../../../shared/components/LoadingState'
import StickerImage from '../../../shared/components/StickerImage'
import { getNotice } from '../api/adminApi'
import type { NoticeResponse } from '../types'

/** 공지 상세 (/notices/:noticeId, 누구나). 홈 배너에서 들어온다 */
export default function NoticeDetail({ noticeId }: { noticeId: number }) {
  const [notice, setNotice] = useState<NoticeResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    getNotice(noticeId)
      .then((res) => !ignore && setNotice(res))
      .catch((e) => !ignore && setError(e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE))
    return () => {
      ignore = true
    }
  }, [noticeId])

  if (error) return <EmptyState sticker="sceneEmpty" message={error} />
  if (!notice) return <LoadingState message="공지를 불러오는 중이에요" />

  return (
    <article className="relative m-4 paper-card p-5">
      <StickerImage name="decoTapeYellow" className="pointer-events-none absolute -top-4 left-6 w-16" ratio="aspect-[3/2]" />
      <h2 className="font-display text-xl leading-snug">{notice.title}</h2>
      <p className="mt-1 text-xs text-neutral-400">{notice.createdAt.slice(0, 10)}</p>
      <p className="mt-4 text-sm leading-relaxed whitespace-pre-wrap">{notice.content}</p>
    </article>
  )
}
