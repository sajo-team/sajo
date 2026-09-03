-- Manual migration: Flyway/Liquibase is not configured, so this file is NOT executed automatically.
ALTER TABLE market_strategy.m_market_stocks_price
    DROP CONSTRAINT IF EXISTS uk_market_stock_price_stock_date_time_source;

ALTER TABLE market_strategy.m_market_stocks_price
    DROP CONSTRAINT IF EXISTS uk_market_stock_price_stock_date;

DROP INDEX IF EXISTS market_strategy.uk_market_stock_price_daily_rest;

-- REST 일봉(time IS NULL)만 정리한다. WEBSOCKET 행은 대상에서 제외한다.
WITH ranked_daily_rows AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY stock_id, date
        ORDER BY created_at ASC NULLS LAST, id ASC
    ) AS row_number
    FROM market_strategy.m_market_stocks_price
    WHERE time IS NULL AND source = 'REST'
)
DELETE FROM market_strategy.m_market_stocks_price price
USING ranked_daily_rows ranked
WHERE price.id = ranked.id
  AND ranked.row_number >= 2;

CREATE UNIQUE INDEX uk_market_stock_price_daily_rest
    ON market_strategy.m_market_stocks_price (stock_id, date)
    WHERE time IS NULL AND source = 'REST';

ALTER TABLE market_strategy.m_market_stocks_price
    ALTER COLUMN current_price DROP NOT NULL;

ALTER TABLE market_strategy.m_market_stocks_price
    ADD COLUMN IF NOT EXISTS close_price BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'market_strategy'
          AND table_name = 'm_market_stocks_price'
          AND column_name = 'trade_amount'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'market_strategy'
          AND table_name = 'm_market_stocks_price'
          AND column_name = 'accumulated_trade_amount'
    ) THEN
        ALTER TABLE market_strategy.m_market_stocks_price
            RENAME COLUMN trade_amount TO accumulated_trade_amount;
    END IF;
END $$;
