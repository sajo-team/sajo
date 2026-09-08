package com.sajo.trading_service.trading.domain.enums;

public enum OrderStatus {
    REQUESTED,
    PROCESSING,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    FAILED,
    TIMEOUT,
    PARTIALLY_FILLED_REJECTED
}
