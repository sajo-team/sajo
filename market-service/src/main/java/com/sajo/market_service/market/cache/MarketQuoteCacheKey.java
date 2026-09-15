package com.sajo.market_service.market.cache;

/** market:quote:* Redis 캐시 키를 만드는 유일한 곳. REST(/quote)와 WebSocket 실시간 갱신이 같은 키를 공유한다. */
public final class MarketQuoteCacheKey {

    private static final String QUOTE_CACHE_KEY_PREFIX = "market:quote:";

    private MarketQuoteCacheKey() {
    }

    public static String of(String stockCode) {
        return QUOTE_CACHE_KEY_PREFIX + stockCode;
    }
}
