package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockPriceQueryRepository;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketStockPriceQueryService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final MarketStockQueryRepository marketStockQueryRepository;
    private final MarketStockPriceQueryRepository marketStockPriceQueryRepository;

    public List<MarketStockPriceResponse> getRecentDailyPrices(String stockCode, int days) {
        validateDays(days);
        MarketStock stock = findStock(stockCode);

        return marketStockPriceQueryRepository
                .findRecentDailyRestPrices(stock.getId(), PriceSource.REST, PageRequest.of(0, days))
                .stream()
                //차트와 백테스트에서 시간순으로 사용하기 위해 과거 → 최신 날짜로 순서를 뒤집는다.
                .sorted(Comparator.comparing(price -> price.getDate()))
                .map(MarketStockPriceResponse::from)
                .toList();
    }

    private MarketStock findStock(String stockCode) {
        return marketStockQueryRepository.findByStockCode(MarketStock.normalizeStockCode(stockCode))
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));
    }

    private void validateDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK_PRICE, "조회 거래일 수는 1일부터 365일까지 가능합니다.");
        }
    }
}
