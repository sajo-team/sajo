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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketStockIndicatorQueryService {

    private static final int MIN_HISTORY_LIMIT = 1;
    private static final int MAX_HISTORY_LIMIT = 40;

    private final MarketStockQueryRepository marketStockQueryRepository;
    private final MarketStockIndicatorQueryRepository marketStockIndicatorQueryRepository;

    public MarketStockIndicatorResponse getLatestIndicator(String stockCode) {
        MarketStock stock = findStock(stockCode);

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
        MarketStock stock = findStock(stockCode);
        // 신규 분기 스냅샷이 아직 적재되지 않은 배포 직후에도 기존 지표를 사용할 수 있다.
        return findLatestEntity(stock.getId())
                .map(MarketStockIndicatorSnapshot::from)
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_INDICATOR_NOT_FOUND));
    }

    /**
     * 종목의 투자지표 이력을 최대 limit건 조회한다.
     *
     * 신규 분기 스냅샷이 하나라도 있으면 그것만 결산연월 내림차순으로 반환하고,
     * 신규 데이터가 전혀 없는 종목에 한해서만 레거시 referenceDate 이력으로 폴백한다.
     * 두 축의 데이터를 하나의 리스트에 섞어서 반환하지 않는다.
     * 지표가 전혀 없으면(종목 자체는 존재) 빈 리스트를 반환한다.
     */
    public List<MarketStockIndicatorResponse> getIndicatorHistory(String stockCode, int limit) {
        validateLimit(limit);
        MarketStock stock = findStock(stockCode);

        List<MarketStockIndicator> financialHistory = marketStockIndicatorQueryRepository
                .findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                        stock.getId(), PageRequest.of(0, limit));

        List<MarketStockIndicator> history = financialHistory.isEmpty()
                ? marketStockIndicatorQueryRepository.findByStockIdAndReferenceDateIsNotNullOrderByReferenceDateDescCreatedAtDesc(
                        stock.getId(), PageRequest.of(0, limit))
                : financialHistory;

        return history.stream().map(MarketStockIndicatorResponse::from).toList();
    }

    private Optional<MarketStockIndicator> findLatestEntity(UUID stockId) {
        Optional<MarketStockIndicator> financial = marketStockIndicatorQueryRepository
                .findTopByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(stockId);
        return financial.isPresent() ? financial
                : marketStockIndicatorQueryRepository.findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(stockId);
    }

    private MarketStock findStock(String stockCode) {
        return marketStockQueryRepository.findByStockCode(MarketStock.normalizeStockCode(stockCode))
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND)); //종목 자체가 없음
    }

    private void validateLimit(int limit) {
        if (limit < MIN_HISTORY_LIMIT || limit > MAX_HISTORY_LIMIT) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_INDICATOR,
                    "조회 건수는 1건부터 40건까지 가능합니다."
            );
        }
    }
}
