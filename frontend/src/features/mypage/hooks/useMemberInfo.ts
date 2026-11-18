import { useEffect, useState } from 'react'
import { ApiError } from '../../../shared/api/apiError'
import { useAuthStore } from '../../../shared/auth/authStore'
import { getMyInfo } from '../api/memberApi'
import type { MemberResponse } from '../types'

export function useMemberInfo(enabled = true) {
  const token = useAuthStore((s) => s.accessToken)
  const [revision, setRevision] = useState(0)
  const key = `${token ?? ''}:${revision}`
  const [state, setState] = useState<{ key: string; data: MemberResponse | null; error: string | null } | null>(null)
  useEffect(() => {
    if (!enabled || !token) return
    let active = true
    void getMyInfo().then((data) => {
      if (!active || useAuthStore.getState().accessToken !== token) return
      useAuthStore.getState().updateUser(data)
      setState({ key, data, error: null })
    }).catch((error: unknown) => {
      if (active) setState({ key, data: null, error: error instanceof ApiError ? error.message : '회원 정보를 불러오지 못했어요.' })
    })
    return () => { active = false }
  }, [enabled, token, key])
  const current = enabled && token && state?.key === key ? state : null
  return {
    data: current?.data ?? null,
    error: current?.error ?? null,
    loading: enabled && Boolean(token) && current === null,
    retry: () => setRevision((value) => value + 1),
  }
}
