package com.sajo.trading_service.trading.domain.policy;

public final class TradingOrderPolicy {

    public static final int MAX_RECONCILIATION_RETRY_COUNT = 3;
    public static final String KIS_RECONCILIATION_EXHAUSTED =
            "KIS_RECONCILIATION_EXHAUSTED";

    private TradingOrderPolicy() {
    }
}