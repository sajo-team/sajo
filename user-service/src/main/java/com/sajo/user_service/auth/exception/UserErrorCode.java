package com.sajo.user_service.auth.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_0001", "이미 사용 중인 이메일입니다"),

    // 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다 (계정 존재 여부가 노출되지 않도록)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "USER_0002", "이메일 또는 비밀번호가 올바르지 않습니다");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
