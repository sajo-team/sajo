-- Manual migration: Flyway/Liquibase is not configured, so this file is NOT executed automatically.
-- MarketRealtimePriceScheduler가 1분마다 WEBSOCKET 스냅샷을 저장한다. 같은 종목·같은 분(minute)에
-- 대한 중복 저장(재기동/재시도로 인한 겹침 등)을 DB 레벨에서 최종적으로 막는다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_market_stock_price_websocket_minute
    ON market_strategy.m_market_stocks_price (stock_id, date, time)
    WHERE time IS NOT NULL AND source = 'WEBSOCKET';
