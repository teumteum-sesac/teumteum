import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../../shared/api/apiError'
import { useAuthStore } from '../../../shared/auth/authStore'
import { getCourse, getCourses } from '../api/courseApi'

// 화면을 떠난 뒤 도착한 응답이나 이전 코스 ID의 데이터를 표시하지 않는다.
function useCourseQuery<T>(key: string, load: () => Promise<T>, enabled = true) {
  const [revision, setRevision] = useState(0)
  const requestKey = `${key}:${revision}`
  const [state, setState] = useState<{ key: string; data: T | null; error: string | null } | null>(null)

  useEffect(() => {
    if (!enabled) return
    let ignore = false
    void load().then((data) => {
      if (!ignore) setState({ key: requestKey, data, error: null })
    }).catch((e: unknown) => {
      if (!ignore) setState({ key: requestKey, data: null, error: e instanceof ApiError ? e.message : '정보를 불러오지 못했어요.' })
    })
    return () => { ignore = true }
  }, [requestKey, load, enabled])

  // 재시도·ID 변경 직후에는 새 응답이 오기 전까지 이전 상세를 표시하지 않는다.
  const current = state?.key === requestKey ? state : null
  return {
    data: enabled ? current?.data ?? null : null,
    loading: enabled && current === null,
    error: enabled ? current?.error ?? null : null,
    retry: () => setRevision((value) => value + 1),
  }
}

export function useSavedCourse(courseId: string) {
  const token = useAuthStore((s) => s.accessToken)
  const load = useCallback(() => getCourse(courseId), [courseId])
  return useCourseQuery(`detail:${token ?? ''}:${courseId}`, load, token !== null)
}

export function useSavedCourseCount(enabled: boolean) {
  const token = useAuthStore((s) => s.accessToken)
  const load = useCallback(() => getCourses(0, 1), [])
  return useCourseQuery(`count:${token ?? ''}`, load, enabled && token !== null)
}
