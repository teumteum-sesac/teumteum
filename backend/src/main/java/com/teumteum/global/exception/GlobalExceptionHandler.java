package com.teumteum.global.exception;

import com.teumteum.global.common.ApiResponse;
import com.teumteum.global.common.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 모든 예외를 공통 응답(ApiResponse) 형식으로 바꾼다.
 * <ul>
 *   <li>응답에 e.getMessage() 나 스택 트레이스를 넣지 않는다. 로그에만 남긴다 (NFR-005)</li>
 *   <li>Spring Security 필터 단계의 401·403(토큰 없음 등)은 여기서 잡히지 않는다.
 *       SecurityConfig 의 AuthenticationEntryPoint·AccessDeniedHandler 에서 같은 형식으로 응답한다 (김용현)</li>
 *   <li>오류는 ResponseStatusException 이 아니라 CustomException(ErrorCode) 로 던진다</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    /**
     * 비즈니스 오류. 5xx 면 원인까지 error 로그, 그 외는 warn
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("비즈니스 오류(서버): {}", errorCode.getCode(), e);
        } else {
            log.warn("비즈니스 오류: {} - {}", errorCode.getCode(), e.getMessage());
        }
        return toResponse(errorCode);
    }

    /**
     * @Valid @RequestBody 검증 실패
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<FieldErrorDetail> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("요청 본문 검증 실패: {}", fieldErrors);
        return toResponse(CommonErrorCode.INVALID_REQUEST, fieldErrors);
    }

    /**
     * @RequestParam·@PathVariable 에 붙인 검증(@Min, @NotBlank 등) 실패.
     * 같은 메서드에 @Valid @RequestBody 가 함께 있으면 본문 오류도 여기로 오므로, 본문 필드는 실제 필드 이름을 쓴다
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        List<FieldErrorDetail> fieldErrors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(fieldName(result, error), error.getDefaultMessage())))
                .toList();
        log.warn("파라미터 검증 실패: {}", fieldErrors);
        return toResponse(CommonErrorCode.INVALID_REQUEST, fieldErrors);
    }

    /**
     * 클래스에 @Validated 를 붙여 검증할 때의 실패
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorDetail> fieldErrors = e.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        lastNodeName(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        log.warn("제약 조건 검증 실패: {}", fieldErrors);
        return toResponse(CommonErrorCode.INVALID_REQUEST, fieldErrors);
    }

    /**
     * 잘못된 JSON, 타입 오류, 필수 파라미터·헤더 누락.
     * 예외 메시지에 요청 본문 일부(비밀번호 등)가 섞일 수 있어 로그에는 예외 이름만 남긴다
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("잘못된 요청: {}", e.getClass().getSimpleName());
        return toResponse(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 없는 URL
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return toResponse(CommonErrorCode.NOT_FOUND);
    }

    /**
     * GET 자리에 POST 등
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * JSON 이 아닌 요청 형식
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return toResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * 컨트롤러·서비스 안에서 발생한 인증 오류
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        return toResponse(CommonErrorCode.UNAUTHORIZED);
    }

    /**
     * 컨트롤러·서비스 안에서 발생한 권한 오류 (예: @PreAuthorize).
     * 로그인하지 않은 사용자는 401(로그인 화면으로 이동), 로그인했지만 권한이 없으면 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || trustResolver.isAnonymous(authentication)) {
            return toResponse(CommonErrorCode.UNAUTHORIZED);
        }
        return toResponse(CommonErrorCode.FORBIDDEN);
    }

    /**
     * 예상하지 못한 모든 예외.
     * 위에서 처리하지 않은 Spring MVC 요청 오류(406, 413 등 4xx)는 원래 상태 코드를 유지하고, 나머지는 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("처리하지 않은 요청 오류: {} - {}", status.value(), e.getClass().getSimpleName());
            return ResponseEntity.status(status).body(ApiResponse.fail(toCommonErrorCode(status)));
        }
        log.error("예상하지 못한 오류", e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    // ===== 공통 응답 생성 =====

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, fieldErrors));
    }

    /**
     * 4xx 상태에 맞는 공통 코드. 딱 맞는 코드가 없으면 INVALID_REQUEST
     */
    private ErrorCode toCommonErrorCode(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) return CommonErrorCode.UNAUTHORIZED;
        if (status.isSameCodeAs(HttpStatus.FORBIDDEN)) return CommonErrorCode.FORBIDDEN;
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) return CommonErrorCode.NOT_FOUND;
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) return CommonErrorCode.METHOD_NOT_ALLOWED;
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) return CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
        return CommonErrorCode.INVALID_REQUEST;
    }

    /**
     * 본문 객체의 필드 오류면 필드 이름("name"), 단순 파라미터면 파라미터 이름("size")
     */
    private String fieldName(ParameterValidationResult result, MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }
        return result.getMethodParameter().getParameterName();
    }

    /**
     * "search.size" 같은 경로에서 마지막 이름("size")만 꺼낸다
     */
    private String lastNodeName(Path propertyPath) {
        String name = null;
        for (Path.Node node : propertyPath) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name != null ? name : "request";
    }
}
