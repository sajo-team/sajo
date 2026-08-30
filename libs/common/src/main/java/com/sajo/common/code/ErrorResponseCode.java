package com.sajo.common.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum ErrorResponseCode implements ErrorCode {

    INVALID_BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_0001", "입력값이 유효하지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_0002", "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_0003", "접근 권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_0004", "리소스를 찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_0005", "허용되지 않은 메서드입니다"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_0006", "요청 본문을 읽을 수 없습니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_9999", "서버 내부 오류가 발생했습니다");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
