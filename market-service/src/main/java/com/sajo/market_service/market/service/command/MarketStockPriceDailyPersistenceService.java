package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import com.sajo.market_service.market.repository.command.MarketStockPriceDailyRestWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DB 저장 트랜잭션 + 기존 날짜 필터링 담당
 */
@Service
@RequiredArgsConstructor
public class MarketStockPriceDailyPersistenceService {

    private final MarketStockPriceCommandRepository marketStockPriceCommandRepository;
    private final MarketStockPriceDailyRestWriter marketStockPriceDailyRestWriter;

    @Transactional
    public int saveDailyPrices(UUID stockId, LocalDate startDate, LocalDate endDate, List<DailyPriceResponse> prices) {
        //개선사항 : 기존 날짜 일괄 조회로 N+1 개선
        Set<LocalDate> existingDates = marketStockPriceCommandRepository
                .findDatesByStockIdAndDateBetweenAndTimeIsNullAndSource(stockId, startDate, endDate, PriceSource.REST);
        Set<LocalDate> knownDates = new HashSet<>(existingDates);
        List<DailyPriceResponse> newPrices = prices.stream()
                .filter(price -> knownDates.add(price.tradeDate()))
                .toList();

        //저장할 데이터가 없으면 바로 종료한다.
        if (newPrices.isEmpty()) {
            return 0;
        }

        // 동시 요청으로 발생하는 중복은 Partial Unique Index가 최종적으로 차단하며,
        // 중복된 행은 ON CONFLICT DO NOTHING으로 건너뛴다.
        return marketStockPriceDailyRestWriter.insertIgnoringDuplicates(stockId, newPrices);
    }
}
