import { apiDelete, apiGet, apiPost } from '../../../shared/api/request'
import type { CourseDeleteResponse, CourseListResponse, CourseResponse, CourseSaveRequest, CourseSaveResponse } from '../types'

export function saveCourse(body: CourseSaveRequest) {
  return apiPost<CourseSaveResponse>('/courses', body)
}

// 기본값: 첫 페이지를 20개씩 조회한다.
export function getCourses(page = 0, size = 20) {
    return apiGet<CourseListResponse>('/courses', { page, size })
}

// 지정한 저장 코스의 삭제를 요청한다.
export function deleteCourse(courseId: string) {
    return apiDelete<CourseDeleteResponse>(`/courses/${courseId}`)
}

export function getCourse(courseId: string) {
  return apiGet<CourseResponse>(`/courses/${encodeURIComponent(courseId)}`)
}
