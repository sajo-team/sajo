package com.sajo.trading_service.trading.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode {

    INVALID_TRADING_LIMIT(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0001", "자동매매 공통 한도 입력값이 유효하지 않습니다"),
    TRADING_LIMIT_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTO_TRADING_0002", "자동매매 공통 한도가 이미 존재합니다"),
    TRADING_LIMIT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTO_TRADING_0003", "자동매매 공통 한도 설정을 찾을 수 없습니다"),
    INVALID_AUTO_TRADING(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0004", "자동매매 설정 입력값이 올바르지 않습니다"),
    TRADING_LIMIT_REQUIRED(HttpStatus.NOT_FOUND, "AUTO_TRADING_0005", "자동매매 공통 한도가 설정되어 있지 않습니다"),
    STRATEGY_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTO_TRADING_0006", "해당 전략을 찾을 수 없습니다"),
    AUTO_TRADING_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTO_TRADING_0007", "해당 전략에 대한 자동매매 설정이 이미 존재합니다"),
    AUTO_TRADING_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTO_TRADING_0008", "자동매매 설정을 찾을 수 없습니다"),
    AUTO_TRADING_STATUS_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "AUTO_TRADING_0009", "현재 상태에서 자동매매 설정 변경이 허용되지 않습니다"),
    INVALID_ORDER(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0010", "주문 입력값이 올바르지 않습니다"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTO_TRADING_0011", "주문을 찾을 수 없습니다"),
    AUTO_TRADING_DISABLED(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0012", "자동매매가 비활성화되어 있습니다."),
    ORDER_QUANTITY_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0013", "주문 금액이 현재 주가보다 작아 주문 가능한 수량이 없습니다."),
    DAILY_ORDER_COUNT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0014", "일일 최대 주문 횟수를 초과하였습니다."),
    DAILY_ORDER_AMOUNT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0015","일일 최대 주문 금액을 초과하였습니다."),
    INVALID_TRADING_SIGNAL(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0016","유효하지 않은 매매 Signal입니다."),
    ORDER_QUANTITY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "AUTO_TRADING_0017", "주문 수량이 허용 범위를 초과했습니다."),

    ;


    private final HttpStatus status;
    private final String errorCode;
    private final String message;

}
