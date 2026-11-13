package com.teumteum.global.common;

import com.teumteum.global.exception.ErrorCode;

import java.util.List;

/**
 * 모든 API 의 공통 응답 형식.
 * <pre>
 * 성공: { "success": true,  "data": { ... }, "error": null }
 * 실패: { "success": false, "data": null,    "error": { "code": "...", "message": "..." } }
 * </pre>
 * HTTP 상태 코드는 여기서 정하지 않고 컨트롤러의 ResponseEntity 가 실제 상태(200, 201, 400 ...)로 정한다.
 * <pre>
 * return ResponseEntity.ok(ApiResponse.ok(response));
 * return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
 * </pre>
 * 정적 메서드 이름을 success() 로 지으면 record 필드 success 의 접근자와 겹쳐 컴파일 오류가 난다.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error
) {

    /** 성공 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 돌려줄 데이터가 없는 성공 (예: 삭제) */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 실패 */
    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ErrorDetail.of(errorCode));
    }

    /** 입력값 검증 실패 */
    public static ApiResponse<Void> fail(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        return new ApiResponse<>(false, null, ErrorDetail.of(errorCode, fieldErrors));
    }
}
