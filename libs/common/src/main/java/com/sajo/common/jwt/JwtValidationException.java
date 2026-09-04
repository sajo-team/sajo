package com.sajo.common.jwt;

// 서명 불일치/만료/형식 오류 등 모든 JWT 검증 실패를 이 예외 하나로 통일한다 (실패 원인 노출 방지)
public class JwtValidationException extends RuntimeException {

    public JwtValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}