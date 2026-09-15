ALTER TABLE trading.p_orders
    ADD COLUMN IF NOT EXISTS market_retry_count INTEGER NOT NULL DEFAULT 0;