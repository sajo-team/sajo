package com.sajo.user_service.account.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements ErrorCode {

    DUPLICATE_ACCOUNT_NO(HttpStatus.CONFLICT, "ACCOUNT_0001", "이미 등록된 계좌번호입니다"),

    ALREADY_HAS_ACCOUNT(HttpStatus.CONFLICT, "ACCOUNT_0002", "이미 등록된 계좌가 있습니다"),

    DUPLICATE_ACCOUNT_REQUEST(HttpStatus.CONFLICT, "ACCOUNT_0003", "이미 처리된 요청입니다"),

    KIS_TOKEN_ISSUE_FAILED(HttpStatus.BAD_GATEWAY, "ACCOUNT_0004", "KIS 토큰 발급에 실패했습니다"),

    INVALID_KIS_CREDENTIALS(HttpStatus.BAD_REQUEST, "ACCOUNT_0005", "유효하지 않은 appKey/secretKey입니다"),

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND,"ACCOUNT_0006" , "해당 계좌를 찾을 수 없습니다" ),

    KIS_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_0007", "KIS 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
