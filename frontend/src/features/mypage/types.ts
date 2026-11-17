// 백엔드 CourseSummaryResponse에 대응하는 코스 요약 타입이다.
export interface CourseSummaryResponse {
    courseId: string
    sourceRecommendationId: string
    courseName: string
    placeCount: number
    totalMinutes: number
    createdAt: string
}

// 백엔드 CourseListResponse에 대응한다.
export interface CourseListResponse {
    // 현재 페이지의 코스 요약 목록
    content: CourseSummaryResponse[]

    // 현재 페이지 번호. 첫 페이지는 0
    page: number

    // 한 페이지에 요청한 개수
    size: number

    // 본인의 전체 저장 코스 수
    totalElements: number

    // 전체 페이지 수
    totalPages: number

    // 다음 페이지 존재 여부
    hasNext: boolean
}

// 백엔드 CourseDeleteResponse에 대응한다.
export interface CourseDeleteResponse {
    // 삭제한 저장 코스 ID
    courseId: string

    // 삭제 성공 여부
    deleted: boolean
}
