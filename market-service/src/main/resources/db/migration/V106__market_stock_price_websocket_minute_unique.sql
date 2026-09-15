-- Flyway migration (#218 도입 이후 신규): baseline-version=105보다 버전이 높아,
-- 배포 시 Flyway가 애플리케이션 기동 과정에서 자동으로 실행한다(수동 psql 적용 불필요).
-- MarketRealtimePriceScheduler가 1분마다 WEBSOCKET 스냅샷을 저장한다. 같은 종목·같은 분(minute)에
-- 대한 중복 저장(재기동/재시도로 인한 겹침 등)을 DB 레벨에서 최종적으로 막는다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_market_stock_price_websocket_minute
    ON market_strategy.m_market_stocks_price (stock_id, date, time)
    WHERE time IS NOT NULL AND source = 'WEBSOCKET';
