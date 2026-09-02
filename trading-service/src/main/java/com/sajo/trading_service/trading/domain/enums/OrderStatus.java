package com.sajo.trading_service.trading.domain.enums;

public enum OrderStatus {
    REQUESTED,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    FAILED,
    TIMEOUT
}
