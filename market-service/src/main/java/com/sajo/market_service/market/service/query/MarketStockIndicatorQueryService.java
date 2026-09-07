package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockIndicatorQueryRepository;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketStockIndicatorQueryService {

    private final MarketStockQueryRepository marketStockQueryRepository;
    private final MarketStockIndicatorQueryRepository marketStockIndicatorQueryRepository;

    public MarketStockIndicatorResponse getLatestIndicator(String stockCode) {
        MarketStock stock = marketStockQueryRepository.findByStockCode(MarketStock.normalizeStockCode(stockCode))
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND)); //종목 자체가 없음

        return marketStockIndicatorQueryRepository.findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(stock.getId())
                .map(MarketStockIndicatorResponse::from)
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_INDICATOR_NOT_FOUND)); //종목은 있음 투자지표만 없음
    }
}
