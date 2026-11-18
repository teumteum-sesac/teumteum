import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../shared/api/apiError'
import { useAuthStore } from '../../../shared/auth/authStore'
import { getLatestCoursePlaces } from '../api/courseApi'
import type { CourseLatestPlacesResponse } from '../types'

// 자동 호출하지 않는다. 상세 컴포넌트는 courseId를 key로 사용해 재진입 시 이 상태를 비운다.
export function useLatestCoursePlaces(courseId: string) {
  const [data, setData] = useState<CourseLatestPlacesResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const active = useRef(true)
  const busy = useRef(false)
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])
  async function refresh() {
    if (busy.current) return
    busy.current = true
    setLoading(true)
    setError(null)
    const token = useAuthStore.getState().accessToken
    try {
      const result = await getLatestCoursePlaces(courseId)
      if (!active.current || useAuthStore.getState().accessToken !== token) return
      if (result.courseId !== courseId) throw new Error('Unexpected course response')
      setData(result)
    } catch (e) {
      if (active.current) setError(e instanceof ApiError ? e.message : '최신 정보를 불러오지 못했어요.')
    } finally {
      busy.current = false
      if (active.current) setLoading(false)
    }
  }
  return { data, loading, error, refresh }
}
