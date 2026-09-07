package com.sajo.market_service.market.service.query;

import com.sajo.market_service.market.dto.response.MarketStockResponse;

import java.util.UUID;

/** Summary 조합에 필요한 내부 종목 조회 결과다. stockId는 HTTP 응답으로 전달하지 않는다. */
public record MarketStockQueryTarget(UUID stockId, MarketStockResponse response) {
}
