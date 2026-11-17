package com.teumteum.recommend.exception;

import com.teumteum.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 추천 도메인 오류 코드 (recommend-api.md 공통 규칙 · 각 API 실패 응답 표).
 * <p>
 * 공통 ErrorCode 를 구현하므로 throw new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED) 로 던지면
 * GlobalExceptionHandler 가 HTTP 상태·code·message 를 ApiResponse 실패 형식으로 바꿔 준다.
 * 모양은 다른 도메인(CourseErrorCode)과 같게 맞췄다.
 */
@Getter
@RequiredArgsConstructor
public enum RecommendErrorCode implements ErrorCode {

    /** 없는 ID, 또는 만료 후 정리됐거나 서버 재시작으로 사라진 ID */
    RECOMMENDATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RECOMMENDATION_NOT_FOUND",
            "추천 결과를 찾을 수 없어요. 다시 추천받아 주세요."
    ),

    /** 장소를 교체하면 이용 시간을 넘음 (6단계 장소 교체에서 사용) */
    AVAILABLE_TIME_EXCEEDED(
            HttpStatus.CONFLICT,
            "AVAILABLE_TIME_EXCEEDED",
            "선택한 시간을 넘어요."
    ),

    /** 생성 후 2시간이 지난 추천 */
    RECOMMENDATION_EXPIRED(
            HttpStatus.GONE,
            "RECOMMENDATION_EXPIRED",
            "추천이 만료되었습니다."
    ),

    /** 다시 추천을 너무 자주 누름 (5-3 에서 사용) */
    TOO_MANY_REQUESTS(
            HttpStatus.TOO_MANY_REQUESTS,
            "TOO_MANY_REQUESTS",
            "잠시 후 다시 시도해 주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
