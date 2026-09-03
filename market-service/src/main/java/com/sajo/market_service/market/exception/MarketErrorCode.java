package com.sajo.market_service.market.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MarketErrorCode implements ErrorCode {

    INVALID_MARKET_STOCK(
            HttpStatus.BAD_REQUEST,
            "MARKET_0001",
            "종목 정보 입력값이 유효하지 않습니다"
    ),

    INVALID_MARKET_STOCK_INDICATOR(
            HttpStatus.BAD_REQUEST,
            "MARKET_0002",
            "투자지표 입력값이 유효하지 않습니다"
    ),

    INVALID_MARKET_STOCK_PRICE(
            HttpStatus.BAD_REQUEST,
            "MARKET_0003",
            "시세 입력값이 유효하지 않습니다"
    ),

    KIS_QUOTE_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY,
            "MARKET_0004",
            "KIS 현재가 응답 처리에 실패했습니다"
    ),

    QUOTE_CACHE_LOCK_TIMEOUT(
            HttpStatus.SERVICE_UNAVAILABLE,
            "MARKET_0005",
            "현재가 조회 동시 요청 처리 시간이 초과되었습니다"
    ),

    MARKET_STOCK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MARKET_0006",
            "종목 정보를 찾을 수 없습니다"
    );

    private final HttpStatus status;
    private final String errorCode;
    private final String message;

}
