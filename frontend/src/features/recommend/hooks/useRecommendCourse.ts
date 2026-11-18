import { useEffect, useMemo, useState } from 'react'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import type { RouteLegResponse } from '../../map/routePath'
import { getRecommendCourse } from '../api/recommendApi'
import { mockSinglePlaceCourse } from '../mock'
import { detailToCourse, type Course, type RecommendCourseDetailResponse } from '../types'

/*
 * [이 파일이 하는 일]
 * 코스 상세(DET-01)·코스 지도(DET-02)·장소 교체(DET-03)가 쓰는 "코스 1개 불러오기".
 *   - 추천 코스: GET /api/recommendations/courses/{recommendationId} 로 서버에서 조회 (새로고침·주소 직접 진입도 동작)
 *   - "place-<장소ID>": 홈 검색·주변 탐색에서 고른 장소 1곳짜리 코스. 장소 상세 API(정종일) 전이라 [임시] 목업
 * reloadKey 를 바꾸면 다시 조회한다 (장소 교체 후 최신 코스를 받기 위해).
 */
export interface RecommendCourseState {
  course: Course | null
  /** 지도 경로 구간 (추천 코스만) */
  routeLegs: RouteLegResponse[] | undefined
  /** 이용 시간(분). 교체 시 시간 초과 판단 */
  availableMinutes: number | null
  loading: boolean
  /** 실패 코드·문구. 410·404 면 만료 안내 */
  error: { code: string; message: string } | null
}

type Loaded = { id: string; reloadKey: unknown; detail: RecommendCourseDetailResponse | null; error: RecommendCourseState['error'] }

export function useRecommendCourse(courseId: string, reloadKey?: unknown): RecommendCourseState {
  const single = useMemo(() => mockSinglePlaceCourse(courseId), [courseId])
  const [loaded, setLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    if (single) return
    let ignore = false
    getRecommendCourse(courseId)
      .then((detail) => !ignore && setLoaded({ id: courseId, reloadKey, detail, error: null }))
      .catch((e) => {
        if (ignore) return
        const error = e instanceof ApiError ? { code: e.code, message: e.message } : { code: 'UNKNOWN', message: DEFAULT_ERROR_MESSAGE }
        setLoaded({ id: courseId, reloadKey, detail: null, error })
      })
    return () => {
      ignore = true
    }
  }, [courseId, reloadKey, single])

  // 지도가 매번 다시 그려지지 않게 같은 응답이면 같은 객체를 돌려준다
  const current = loaded && loaded.id === courseId && loaded.reloadKey === reloadKey ? loaded : null
  const detail = current?.detail ?? null
  const course = useMemo(() => (detail ? detailToCourse(detail) : null), [detail])

  if (single) return { course: single, routeLegs: undefined, availableMinutes: null, loading: false, error: null }
  return {
    course,
    routeLegs: detail?.routeLegs,
    availableMinutes: detail?.availableMinutes ?? null,
    loading: current === null,
    error: current?.error ?? null,
  }
}
