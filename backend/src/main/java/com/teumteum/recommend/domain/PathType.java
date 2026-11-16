package com.teumteum.recommend.domain;

/**
 * 지도에 그리는 경로 종류. (course-api.md CourseRouteLegResponse 와 같은 값)
 */
public enum PathType {
    ACTUAL,     // 실제 경로 (차량·도보 길찾기 API 결과)
    STRAIGHT    // 직선 (대중교통 1차 버전, 이동시간 API 실패로 직선거리 대체한 구간)
}
