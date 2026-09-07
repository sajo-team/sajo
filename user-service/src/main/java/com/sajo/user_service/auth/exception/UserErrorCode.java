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
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "USER_0002", "이메일 또는 비밀번호가 올바르지 않습니다"),

    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "USER_0003", "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요"),

    // 존재하지 않는 토큰과 재사용(탈취) 감지된 토큰을 구분하지 않는다 - 어느 경우든
    // 클라이언트가 할 일은 동일하게 재로그인뿐이고, 구분해서 알려주면 공격자에게
    // "탐지됐다"는 정보를 줄 수 있다
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER_0004", "리프레시 토큰이 유효하지 않습니다. 다시 로그인해주세요");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
