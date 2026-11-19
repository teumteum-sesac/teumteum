package com.teumteum.place.exception;

import com.teumteum.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 장소 도메인 오류 코드.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceErrorCode implements ErrorCode {

    INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "INVALID_COORDINATE", "위도와 경도를 함께 입력해 주세요."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "KAKAO_API_ERROR", "장소 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."),
    KAKAO_API_RATE_LIMIT(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_API_RATE_LIMIT", "요청이 많아요. 잠시 후 다시 시도해 주세요."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "카테고리는 1~3개 선택해 주세요."),
    INVALID_RADIUS(HttpStatus.BAD_REQUEST, "INVALID_RADIUS", "검색 반경이 올바르지 않습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "장소 정보를 찾을 수 없어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
