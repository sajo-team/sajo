package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.command.MarketStockCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * KIS에서 과거 일별 시세를 받아서 PostgreSQL에 저장
 * 외부 API 호출 담당
 */
@Service
@RequiredArgsConstructor
public class MarketStockPriceCommandService {
    private final MarketStockCommandRepository marketStockCommandRepository;
    private final UserAccountFeignClient userAccountFeignClient;
    private final KisApiClient kisApiClient;
    private final MarketStockPriceDailyPersistenceService marketStockPriceDailyPersistenceService;

    public int collectAndSaveDailyPrices(UUID userId, String stockCode, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        MarketStock stock = marketStockCommandRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "존재하지 않는 종목입니다."));
        return collectAndSaveDailyPricesForIdentifiedStock(userId, stock.getId(), stockCode, startDate, endDate);
    }

    /**
     * Collects a stock that was already identified by an upstream query, avoiding another stock lookup.
     */
    public int collectAndSaveDailyPricesForIdentifiedStock(
            UUID userId,
            UUID stockId,
            String stockCode,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);
        if (stockId == null) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "종목 식별자가 올바르지 않습니다.");
        }

        var credentials = userAccountFeignClient.getKisToken(userId);
        List<DailyPriceResponse> prices = kisApiClient.getDailyPrices(credentials, stockCode, startDate, endDate);
        return marketStockPriceDailyPersistenceService.saveDailyPrices(stockId, startDate, endDate, prices);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK_PRICE, "조회 날짜 범위가 올바르지 않습니다.");
        }
    }
}
