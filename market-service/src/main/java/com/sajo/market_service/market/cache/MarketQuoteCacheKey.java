package com.sajo.market_service.market.cache;

/** market:quote:* Redis 캐시 키를 만드는 유일한 곳. REST(/quote)와 WebSocket 실시간 갱신이 같은 키를 공유한다. */
public final class MarketQuoteCacheKey {

    private static final String QUOTE_CACHE_KEY_PREFIX = "market:quote:";
    private static final String NO_PREVIOUS_CLOSE_PRICE_MARKER_KEY_PREFIX = "market:quote:no-previous-close:";

    private MarketQuoteCacheKey() {
    }

    public static String of(String stockCode) {
        return QUOTE_CACHE_KEY_PREFIX + stockCode;
    }

    /**
     * KIS REST 응답 자체에 previousClosePrice가 없다고 확인된 종목을 previousClosePriceMissingTtl 동안 표시해두는 마커 키
     * (#228, 코드 리뷰 반영). {@code MarketQuoteQueryService}가 매 호출마다 KIS를 재확인하지 않도록
     * 이 마커의 존재 여부로 "이미 확인된 부재"인지 판단한다.
     */
    public static String noPreviousClosePriceMarkerKey(String stockCode) {
        return NO_PREVIOUS_CLOSE_PRICE_MARKER_KEY_PREFIX + stockCode;
    }
}
