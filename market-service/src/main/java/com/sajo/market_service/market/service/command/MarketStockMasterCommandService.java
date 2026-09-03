package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.dto.command.MarketStockMasterCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 외부 수집기가 전달한 마스터 데이터만 저장한다. 외부 API 호출은 이 Service의 책임이 아니다.
 */
@Service
@RequiredArgsConstructor
public class MarketStockMasterCommandService {

    private final MarketStockMasterPersistenceService marketStockMasterPersistenceService;

    public int saveMasterStocks(List<MarketStockMasterCommand> stocks) {
        return marketStockMasterPersistenceService.upsertMasterStocks(stocks);
    }
}
