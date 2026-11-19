import { useCallback, useEffect, useState } from 'react'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import Button from '../../../shared/components/Button'
import ConfirmDialog from '../../../shared/components/ConfirmDialog'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import LoadingState from '../../../shared/components/LoadingState'
import { getAdminMembers, withdrawMember } from '../api/adminApi'
import type { AdminMemberResponse, PageResponse } from '../types'

/*
 * [이 파일이 하는 일]
 * 관리자 회원 관리 (REQ-045): 이메일·닉네임 검색, 최신 가입순 목록, [더 보기], 강제 탈퇴.
 * 관리자 회원·이미 탈퇴한 회원에게는 탈퇴 버튼을 보여 주지 않는다 (서버도 막는다).
 */
export default function AdminMembers() {
  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')
  const [data, setData] = useState<PageResponse<AdminMemberResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [target, setTarget] = useState<AdminMemberResponse | null>(null)
  const [withdrawing, setWithdrawing] = useState(false)
  const [withdrawError, setWithdrawError] = useState<string | null>(null)

  /** page 0 이면 목록을 새로, 그 뒤 페이지면 이어 붙인다. 로딩 표시 켜기는 부르는 쪽(버튼)이 한다 */
  const load = useCallback(async (q: string, page: number) => {
    try {
      const res = await getAdminMembers(q, page)
      setError(null)
      setData((prev) => (page === 0 || !prev ? res : { ...res, content: [...prev.content, ...res.content] }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [])

  // 검색어가 바뀌면 첫 페이지부터 다시 (화면을 떠난 뒤 온 응답은 무시)
  useEffect(() => {
    let ignore = false
    getAdminMembers(query, 0)
      .then((res) => {
        if (ignore) return
        setData(res)
        setError(null)
      })
      .catch((e) => !ignore && setError(e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [query])

  const handleWithdraw = async () => {
    if (!target) return
    setWithdrawing(true)
    setWithdrawError(null)
    try {
      const updated = await withdrawMember(target.memberId)
      setData((prev) => prev && { ...prev, content: prev.content.map((m) => (m.memberId === updated.memberId ? updated : m)) })
      setTarget(null)
    } catch (e) {
      setWithdrawError(e instanceof ApiError ? e.message : DEFAULT_ERROR_MESSAGE)
    } finally {
      setWithdrawing(false)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          setLoading(true)
          setQuery(keyword.trim())
        }}
        className="flex h-11 items-center gap-2 rounded-2xl border-2 border-ink bg-cream px-3 shadow-[0_3px_0_var(--color-ink)]"
      >
        <Icon name="search" className="size-5" />
        <input
          type="search"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="이메일·닉네임 검색"
          aria-label="회원 검색"
          className="h-full min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-400"
        />
      </form>

      {data && <p className="px-1 text-xs text-neutral-500">회원 {data.totalElements}명</p>}

      {error && (
        <p role="alert" className="flex items-center justify-between rounded-xl bg-red-50 px-3 py-2 text-sm text-red-600">
          {error}
          <button
            type="button"
            onClick={() => {
              setLoading(true)
              void load(query, 0)
            }}
            className="font-bold underline"
          >
            다시 시도
          </button>
        </p>
      )}

      {!data && loading && <LoadingState message="회원을 불러오는 중이에요" />}
      {data && data.content.length === 0 && <EmptyState message={query ? `‘${query}’ 회원이 없어요` : '회원이 없어요'} />}

      <ul className="flex flex-col gap-2">
        {data?.content.map((m) => (
          <li key={m.memberId} className="paper-card flex items-center gap-3 p-3">
            <div className="min-w-0 flex-1">
              <p className="flex items-center gap-1.5 truncate font-bold">
                {m.nickname}
                {m.admin && <span className="rounded-full bg-sunny px-1.5 text-[10px]">관리자</span>}
                {m.accountStatus === 'WITHDRAWN' && <span className="rounded-full bg-paper px-1.5 text-[10px] text-neutral-500">탈퇴</span>}
              </p>
              <p className="truncate text-xs text-neutral-500">
                #{m.memberId} · {m.email} · {m.createdAt.slice(0, 10)} 가입
              </p>
            </div>
            {!m.admin && m.accountStatus === 'ACTIVE' && (
              <button
                type="button"
                onClick={() => {
                  setWithdrawError(null)
                  setTarget(m)
                }}
                className="shrink-0 rounded-full border-2 border-red-200 px-2.5 py-1 text-xs font-bold text-red-600"
              >
                탈퇴 처리
              </button>
            )}
          </li>
        ))}
      </ul>

      {data?.hasNext && (
        <Button
          variant="secondary"
          disabled={loading}
          onClick={() => {
            setLoading(true)
            void load(query, data.page + 1)
          }}
        >
          {loading ? '불러오는 중…' : '더 보기'}
        </Button>
      )}

      {target && (
        <fieldset disabled={withdrawing} className="contents">
          <ConfirmDialog
            message={`${target.nickname} 님을 탈퇴 처리할까요?`}
            description={withdrawError ?? '탈퇴하면 이 계정으로 로그인할 수 없고, 저장한 코스도 함께 삭제돼요.'}
            confirmLabel={withdrawing ? '처리 중…' : '탈퇴 처리'}
            onCancel={() => {
              if (!withdrawing) setTarget(null)
            }}
            onConfirm={() => void handleWithdraw()}
          />
        </fieldset>
      )}
    </div>
  )
}
