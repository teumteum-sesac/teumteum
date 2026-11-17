import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../shared/api/apiError'
import { deleteCourse, getCourses } from '../api/courseApi'
import type { CourseListResponse } from '../types'

// 저장 코스의 목록·추가 조회·삭제와 요청 상태를 관리한다.
export function useSavedCourses() {
  const [data, setData] = useState<CourseListResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const currentData = useRef<CourseListResponse | null>(null)
  const mounted = useRef(false)
  const requestId = useRef(0)
  const fetching = useRef(false)
  const deleting = useRef(false)
  const needsReload = useRef(false)
  const failedPage = useRef(0)

  const updateData = useCallback((response: CourseListResponse) => {
    currentData.current = response
    setData(response)
  }, [])

  const loadPage = useCallback(async (page: number) => {
    if (!mounted.current || fetching.current) return
    fetching.current = true
    const id = ++requestId.current
    failedPage.current = page
    setLoading(true)
    setError(null)

    try {
      const response = await getCourses(page)
      if (!mounted.current || id !== requestId.current) return
      const previous = currentData.current?.content ?? []
      // 첫 페이지는 교체하고, 추가 페이지는 중복 ID를 제외해 기존 목록 뒤에 붙인다.
      const content = page === 0 ? response.content : [
        ...previous,
        ...response.content.filter((course) => !previous.some((item) => item.courseId === course.courseId)),
      ]
      updateData({ ...response, content })
      needsReload.current = false
    } catch (e) {
      // 추가 조회나 재조회가 실패해도 기존 data는 그대로 유지한다.
      if (mounted.current && id === requestId.current) {
        setError(e instanceof ApiError ? e.message : '저장한 코스를 불러오지 못했어요.')
      }
    } finally {
      if (mounted.current && id === requestId.current) {
        fetching.current = false
        setLoading(false)
      }
    }
  }, [updateData])

  useEffect(() => {
    mounted.current = true
    void loadPage(0)
    return () => {
      mounted.current = false
      requestId.current += 1
      fetching.current = false
    }
  }, [loadPage])

  function loadMore() {
    if (deleting.current || !currentData.current?.hasNext) return
    void loadPage(needsReload.current ? 0 : currentData.current.page + 1)
  }

  function retry() {
    if (!deleting.current) void loadPage(needsReload.current ? 0 : failedPage.current)
  }

  async function removeCourse(courseId: string): Promise<boolean> {
    // 같은 렌더 사이의 빠른 연속 클릭도 ref로 차단한다.
    if (!mounted.current || deleting.current || fetching.current) return false
    deleting.current = true
    const id = requestId.current
    setDeletingId(courseId)
    setDeleteError(null)

    try {
      const response = await deleteCourse(courseId)
      if (!mounted.current || id !== requestId.current) return false
      if (!response.deleted || response.courseId !== courseId) throw new Error('Unexpected delete response')

      // 삭제 성공이 확인된 카드만 제거한다. 재조회 실패 시에도 이 카드를 되살리지 않는다.
      const previous = currentData.current
      if (previous) {
        const totalElements = Math.max(0, previous.totalElements - 1)
        updateData({
          ...previous,
          content: previous.content.filter((course) => course.courseId !== courseId),
          totalElements,
          totalPages: Math.ceil(totalElements / previous.size),
        })
      }
      // 삭제로 페이지 경계가 바뀌므로 첫 페이지부터 다시 조회한다.
      needsReload.current = true
      await loadPage(0)
      return true
    } catch (e) {
      if (mounted.current && id === requestId.current) {
        setDeleteError(e instanceof ApiError ? e.message : '저장을 해제하지 못했어요.')
      }
      return false
    } finally {
      deleting.current = false
      if (mounted.current) setDeletingId(null)
    }
  }

  function clearDeleteError() {
    setDeleteError(null)
  }

  return { data, loading, error, deletingId, deleteError, loadMore, retry, removeCourse, clearDeleteError }
}
