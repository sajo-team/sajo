package com.sajo.common.exception;

import com.sajo.common.code.ErrorResponseCode;
import com.sajo.common.feign.FeignApiException;
import com.sajo.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // @RequestBody + @Valid 검증 실패 시
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("uri: {}", request.getRequestURI(), e);
        return ErrorResponse.toResponseEntity(ErrorResponseCode.INVALID_BAD_REQUEST, errors);
    }

    // @RequestParam / @PathVariable + @Validated 검증 실패 시
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException e,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        log.warn("uri: {}", request.getRequestURI(), e);
        return ErrorResponse.toResponseEntity(ErrorResponseCode.INVALID_BAD_REQUEST, errors);
    }

    // 요청 body가 파싱 불가능한 형식(JSON 깨짐 등)일 시
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            HttpServletRequest request) {

        log.warn("uri: {}", request.getRequestURI(), e);
        return ErrorResponse.toResponseEntity(ErrorResponseCode.MALFORMED_REQUEST);
    }

    // 지원하지 않는 HTTP 메서드로 요청 시
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e,
            HttpServletRequest request) {

        log.warn("uri: {}", request.getRequestURI(), e);
        return ErrorResponse.toResponseEntity(ErrorResponseCode.METHOD_NOT_ALLOWED);
    }

    // 인증 실패 시 (로그인 안 됨, 토큰 없음/만료 등)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException e,
            HttpServletRequest request) {

        log.warn("uri: {}", request.getRequestURI(), e);
        return ErrorResponse.toResponseEntity(ErrorResponseCode.UNAUTHORIZED);
    }

    // 인증은 됐지만 해당 리소스에 대한 권한이 없을 시
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException e,
            HttpServletRequest request) {

        log.warn("uri: {}", request.getRequestURI(), e);
        return ErrorResponse.toResponseEntity(ErrorResponseCode.FORBIDDEN);
    }

    // 각 서비스가 직접 던지는 도메인 예외 시
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {

        log.warn("uri: {}, businessException: {}", request.getRequestURI(), e.getMessage());
        return ErrorResponse.toResponseEntity(e.getErrorCode(), e.getMessage());
    }

    // 하위 서비스 Feign 호출 실패 시 (호출한 쪽이 직접 catch해서 도메인 예외로 바꾸지 않은 경우)
    @ExceptionHandler(FeignApiException.class)
    public ResponseEntity<ErrorResponse> handleFeignApiException(FeignApiException e, HttpServletRequest request) {

        log.warn("uri: {}, feignApiException: {} {}", request.getRequestURI(), e.getErrorCode(), e.getMessage());

        HttpStatus status = HttpStatus.resolve(e.getStatus());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        return ResponseEntity.status(status)
                .body(new ErrorResponse(false, e.getErrorCode(), e.getMessage(), null));
    }

    // 위에서 못 잡은 나머지 모든 예외 시 (원인 불명 서버 에러)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {

        log.error("uri: {}", request.getRequestURI(), e);
        return ErrorResponse.toResponseEntity(ErrorResponseCode.INTERNAL_SERVER_ERROR);
    }
}
