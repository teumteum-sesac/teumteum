import { apiDelete, apiGet } from '../../../shared/api/request'
import type { CourseDeleteResponse, CourseListResponse } from '../types'

// 기본값: 첫 페이지를 20개씩 조회한다.
export function getCourses(page = 0, size = 20) {
    return apiGet<CourseListResponse>('/courses', { page, size })
}

// 지정한 저장 코스의 삭제를 요청한다.
export function deleteCourse(courseId: string) {
    return apiDelete<CourseDeleteResponse>(`/courses/${courseId}`)
}
