-- Manual migration: Flyway/Liquibase is not configured, so this file is NOT executed automatically.
ALTER TABLE market_strategy.p_strategy_backtests
    ADD COLUMN IF NOT EXISTS total_return_rate NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS mdd NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS win_rate NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS trade_count INTEGER,
    ADD COLUMN IF NOT EXISTS max_consecutive_losses INTEGER;

