package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockIndicator;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorSnapshot;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockIndicatorQueryRepository;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketStockIndicatorQueryService {

    private final MarketStockQueryRepository marketStockQueryRepository;
    private final MarketStockIndicatorQueryRepository marketStockIndicatorQueryRepository;

    public MarketStockIndicatorResponse getLatestIndicator(String stockCode) {
        MarketStock stock = marketStockQueryRepository.findByStockCode(MarketStock.normalizeStockCode(stockCode))
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND)); //종목 자체가 없음

        return findLatestEntity(stock.getId())
                .map(MarketStockIndicatorResponse::from)
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_INDICATOR_NOT_FOUND)); //종목은 있음 투자지표만 없음
    }

    /** 이미 존재가 확인된 종목의 ID로 최신 지표만 선택 조회한다. */
    public Optional<MarketStockIndicatorResponse> findLatestIndicatorByConfirmedStockId(UUID stockId) {
        return findLatestEntity(stockId)
                .map(MarketStockIndicatorResponse::from);
    }

    public MarketStockIndicatorSnapshot getLatestFinancialIndicator(String stockCode) {
        MarketStock stock = marketStockQueryRepository.findByStockCode(MarketStock.normalizeStockCode(stockCode))
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));
        return marketStockIndicatorQueryRepository
                .findTopByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(stock.getId())
                .map(MarketStockIndicatorSnapshot::from)
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_INDICATOR_NOT_FOUND));
    }

    private Optional<MarketStockIndicator> findLatestEntity(UUID stockId) {
        Optional<MarketStockIndicator> financial = marketStockIndicatorQueryRepository
                .findTopByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(stockId);
        return financial.isPresent() ? financial
                : marketStockIndicatorQueryRepository.findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(stockId);
    }
}
