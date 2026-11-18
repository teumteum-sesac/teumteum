import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import EmptyState from '../../../shared/components/EmptyState'
import TextField from '../../../shared/components/TextField'
import { createNotice, deleteNotice, getNotices, updateNotice } from '../api/adminApi'
import type { NoticeResponse } from '../types'

/*
 * [이 파일이 하는 일]
 * 관리자 공지사항 관리 (REQ-049): 위쪽 작성 폼(등록·수정 겸용) + 아래 목록(수정·삭제).
 * 가장 최근 공지는 홈 상단 배너로도 보인다 (전체 알림 REQ-053 대체).
 */
const TITLE_MAX = 200

export default function AdminNotices() {
  const [notices, setNotices] = useState<NoticeResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // 수정 중인 공지. null 이면 새 공지 작성
  const [editing, setEditing] = useState<NoticeResponse | null>(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<NoticeResponse | null>(null)

  const load = useCallback(async () => {
    try {
      setNotices((await getNotices(0, 50)).content)
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [])

  // 처음 들어올 때 목록 조회 (등록·수정·삭제 뒤에는 load() 로 다시 조회)
  useEffect(() => {
    let ignore = false
    getNotices(0, 50)
      .then((res) => !ignore && setNotices(res.content))
      .catch((e) => !ignore && setError(e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [])

  const resetForm = () => {
    setEditing(null)
    setTitle('')
    setContent('')
    setFormError(null)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !content.trim() || saving) return
    setSaving(true)
    setFormError(null)
    try {
      if (editing) await updateNotice(editing.noticeId, { title, content })
      else await createNotice({ title, content })
      resetForm()
      await load()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : DEFAULT_ERROR_MESSAGE)
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleting) return
    try {
      await deleteNotice(deleting.noticeId)
      if (editing?.noticeId === deleting.noticeId) resetForm()
      setDeleting(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : DEFAULT_ERROR_MESSAGE)
      setDeleting(null)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={handleSubmit} className="paper-card flex flex-col gap-3 p-4" noValidate>
        <p className="font-display text-lg">{editing ? `공지 수정 #${editing.noticeId}` : '새 공지 작성'}</p>
        <TextField label="제목" name="title" maxLength={TITLE_MAX} value={title} onChange={(e) => setTitle(e.target.value)} />
        <label className="flex flex-col gap-1.5">
          <span className="px-1 text-sm font-bold">본문</span>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={5}
            className="rounded-2xl border-2 border-line bg-cream p-3 text-sm outline-none focus:border-primary"
          />
        </label>
        {formError && <p role="alert" className="text-sm text-red-600">{formError}</p>}
        <div className="flex gap-2">
          {editing && (
            <Button variant="secondary" className="w-auto px-5" onClick={resetForm}>
              취소
            </Button>
          )}
          <Button type="submit" variant="pink" disabled={!title.trim() || !content.trim() || saving}>
            {saving ? '저장 중…' : editing ? '수정하기' : '등록하기'}
          </Button>
        </div>
      </form>

      {error && <p role="alert" className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}
      {!loading && notices.length === 0 && !error && <EmptyState message="등록된 공지가 없어요" />}

      <ul className="flex flex-col gap-2">
        {notices.map((n, i) => (
          <li key={n.noticeId} className="paper-card p-3">
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-1.5 font-bold">
                  <span className="truncate">{n.title}</span>
                  {i === 0 && <span className="shrink-0 rounded-full bg-sunny px-1.5 text-[10px]">홈 배너</span>}
                </p>
                <p className="mt-0.5 line-clamp-2 text-xs text-neutral-500">{n.content}</p>
                <p className="mt-1 text-[11px] text-neutral-400">{n.createdAt.slice(0, 16).replace('T', ' ')}</p>
              </div>
              <div className="flex shrink-0 flex-col gap-1">
                <button
                  type="button"
                  onClick={() => {
                    setEditing(n)
                    setTitle(n.title)
                    setContent(n.content)
                    window.scrollTo({ top: 0, behavior: 'smooth' })
                  }}
                  className="rounded-full border-2 border-line px-2.5 py-0.5 text-xs font-bold"
                >
                  수정
                </button>
                <button type="button" onClick={() => setDeleting(n)} className="rounded-full border-2 border-red-200 px-2.5 py-0.5 text-xs font-bold text-red-600">
                  삭제
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>

      {deleting && (
        <ConfirmDialog
          message="이 공지를 삭제할까요?"
          description={deleting.title}
          confirmLabel="삭제"
          onCancel={() => setDeleting(null)}
          onConfirm={() => void handleDelete()}
        />
      )}
    </div>
  )
}
