package com.sajo.market_service.strategy.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StrategyErrorCode implements ErrorCode {
    INVALID_STRATEGY(HttpStatus.BAD_REQUEST, "STRATEGY_0001", "전략 입력값이 유효하지 않습니다."),
    STRATEGY_NOT_FOUND(HttpStatus.NOT_FOUND, "STRATEGY_0002", "전략을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
