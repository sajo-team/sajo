package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.command.MarketStockCommandRepository;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;

/**
 * KIS에서 과거 일별 시세를 받아서 PostgreSQL에 저장
 */
@Service
@RequiredArgsConstructor
public class MarketStockPriceCommandService {
    private final MarketStockCommandRepository marketStockCommandRepository;
    private final MarketStockPriceCommandRepository marketStockPriceCommandRepository;
    private final UserAccountFeignClient userAccountFeignClient;
    private final KisApiClient kisApiClient;

    @Transactional
    public int collectAndSaveDailyPrices(UUID userId, String stockCode, LocalDate startDate, LocalDate endDate) {
        //1.날짜 범위 검증
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK_PRICE, "조회 날짜 범위가 올바르지 않습니다.");
        }
        //2.종목 코드로 내부 종목 조회
        MarketStock stock = marketStockCommandRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "존재하지 않는 종목입니다."));

        //3.KIS 인증정보 조회
        var credentials = userAccountFeignClient.getKisToken(userId);
        int saved = 0;

        //KIS 일별 시세 조회
        for (DailyPriceResponse price : kisApiClient.getDailyPrices(credentials, stockCode, startDate, endDate)) {
            //중복 확인
            if (marketStockPriceCommandRepository.existsByStockIdAndDateAndTimeIsNullAndSource(
                    stock.getId(), price.tradeDate(), PriceSource.REST
            )) continue;


            marketStockPriceCommandRepository.save(MarketStockPrice.create(stock.getId(), price.tradeDate(), null,
                    null, price.closePrice(), price.openPrice(), price.highPrice(), price.lowPrice(), null, null, null,
                    null, price.volume(), price.tradeAmount(), null, PriceSource.REST));
            saved++;
        }
        return saved;
    }
}
