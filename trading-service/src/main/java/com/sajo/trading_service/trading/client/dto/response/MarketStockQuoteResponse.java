package com.sajo.trading_service.trading.client.dto.response;

import java.time.OffsetDateTime;

public record MarketStockQuoteResponse(
        String stockCode,
        Long currentPrice,

        // Market 내부 API에서 제공하는 전일 종가.
        // 캐시 값이 없으면 Market Service가 REST 재조회로 보완하며,
        // Trading에서는 null이면 주문 가격 검증 불가로 처리한다.
        Long previousClosePrice,
        OffsetDateTime baseTime
) {
}
