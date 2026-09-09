package com.sajo.market_service.market.service.command;

public record MarketStockMasterSyncResult(
        int collectedCount,
        int savedCount,
        int skippedCount,
        int failedCount
) {
    public MarketStockMasterSyncResult plus(MarketStockMasterSyncResult other) {
        return new MarketStockMasterSyncResult(
                collectedCount + other.collectedCount,
                savedCount + other.savedCount,
                skippedCount + other.skippedCount,
                failedCount + other.failedCount);
    }
}
