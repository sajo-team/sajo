package com.sajo.market_service.market.runner;

import com.sajo.market_service.market.config.MarketStockMasterSyncProperties;
import com.sajo.market_service.market.service.command.MarketStockMasterSyncResult;
import com.sajo.market_service.market.service.command.MarketStockMasterSyncService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.*;

class MarketStockMasterSyncRunnerTest {
    @Test
    void logsAndSwallowsSyncFailureSoApplicationCanContinue() {
        MarketStockMasterSyncService service = mock(MarketStockMasterSyncService.class);
        when(service.sync(10)).thenThrow(new IllegalStateException("download failed"));
        MarketStockMasterSyncRunner runner = new MarketStockMasterSyncRunner(service,
                new MarketStockMasterSyncProperties(true, 10, 100, Duration.ofSeconds(1), Duration.ofSeconds(1)));

        runner.run(null);

        verify(service).sync(10);
    }
}
