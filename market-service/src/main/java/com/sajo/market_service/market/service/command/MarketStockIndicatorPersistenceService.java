package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.dto.command.MarketStockIndicatorCommand;
import com.sajo.market_service.market.repository.command.MarketStockIndicatorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 투자지표를 DB에 저장하는 짧은 트랜잭션의 시작과 끝을 담당하는 Service
 */
@Service
@RequiredArgsConstructor
public class MarketStockIndicatorPersistenceService {

    private final MarketStockIndicatorWriter marketStockIndicatorWriter;

    @Transactional
    public void save(UUID stockId, MarketStockIndicatorCommand indicator) {
        marketStockIndicatorWriter.upsert(stockId, indicator);
    }
}
