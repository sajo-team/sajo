package com.sajo.trading_service.trading.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode {

    INVALID_TRADING_LIMIT(
            HttpStatus.BAD_REQUEST,
            "AUTO_TRADING_0001",
            "자동매매 공통 한도 입력값이 유효하지 않습니다"
    ),

    TRADING_LIMIT_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "AUTO_TRADING_0002",
            "자동매매 공통 한도가 이미 존재합니다"
    );

    private final HttpStatus status;
    private final String errorCode;
    private final String message;

}
