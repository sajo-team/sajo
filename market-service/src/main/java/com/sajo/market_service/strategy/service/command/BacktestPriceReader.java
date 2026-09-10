package com.sajo.market_service.strategy.service.command;

import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.market.service.query.MarketStockPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BacktestPriceReader {
    // MVP에서는 요청 기간의 일수만큼 최근 일별 시세를 조회한다.
    // 추후 startDate ~ endDate 정확한 기간 조회 방식으로 전환한다.

    private final MarketStockPriceQueryService marketStockPriceQueryService;

    public List<MarketStockPriceResponse> read (
            String stockCode,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // TODO: startDate, endDate 입력을 통한 기간 지정으로 수정 예정
        int days = Math.toIntExact(endDate.toEpochDay() - startDate.toEpochDay()) + 1;

        return marketStockPriceQueryService
                .getRecentDailyPrices(stockCode, days);
    }
}
