package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market.stock-master-sync")
public record MarketStockMasterSyncProperties(
        boolean enabled,
        int chunkSize,
        long maxDownloadBytes,
        Duration connectTimeout,
        Duration readTimeout
) {
    public MarketStockMasterSyncProperties {
        chunkSize = chunkSize > 0 ? chunkSize : 500;
        maxDownloadBytes = maxDownloadBytes > 0 ? maxDownloadBytes : 50 * 1024 * 1024;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }
}
