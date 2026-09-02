package com.sajo.user_service.auth.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_0001", "이미 사용 중인 이메일입니다");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
