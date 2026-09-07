package com.sajo.market_service.market.service.query;

import com.sajo.market_service.market.dto.response.MarketDataStatusResponse;
import com.sajo.market_service.market.repository.query.MarketDataStatusProjection;
import com.sajo.market_service.market.repository.query.MarketDataStatusQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketDataStatusQueryService {

    private final MarketDataStatusQueryRepository marketDataStatusQueryRepository;

    public MarketDataStatusResponse getStatus() {
        MarketDataStatusProjection status = marketDataStatusQueryRepository.findStatus();
        return new MarketDataStatusResponse(
                status.getTotalStockCount(),
                status.getDailyPriceStockCount(),
                status.getLatestDailyPriceDate(),
                status.getIndicatorStockCount(),
                status.getLatestIndicatorReferenceDate()
        );
    }
}
