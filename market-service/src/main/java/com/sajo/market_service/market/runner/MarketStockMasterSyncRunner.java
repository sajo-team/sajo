package com.sajo.market_service.market.runner;

import com.sajo.market_service.market.config.MarketStockMasterSyncProperties;
import com.sajo.market_service.market.service.command.MarketStockMasterSyncResult;
import com.sajo.market_service.market.service.command.MarketStockMasterSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.stock-master-sync", name = "enabled", havingValue = "true")
public class MarketStockMasterSyncRunner implements ApplicationRunner {
    private final MarketStockMasterSyncService syncService;
    private final MarketStockMasterSyncProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            MarketStockMasterSyncResult result = syncService.sync(properties.chunkSize());
            log.info("종목 마스터 동기화 완료: collected={}, saved={}, skipped={}, failed={}", result.collectedCount(), result.savedCount(), result.skippedCount(), result.failedCount());
        } catch (RuntimeException exception) {
            // 일회성 운영 동기화 실패가 Market Service 전체 기동을 막지 않도록 한다.
            log.error("종목 마스터 동기화에 실패했습니다. 기존 종목 데이터는 유지됩니다.", exception);
        }
    }
}
