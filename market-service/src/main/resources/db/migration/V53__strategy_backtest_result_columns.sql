-- Baseline migration (Flyway adoption, #218): this change was already applied manually via psql
-- before Flyway was introduced. It is kept here for history only — Flyway's baselineVersion=105
-- (see application.yaml) means this file is NOT re-executed on any existing market_strategy schema.
ALTER TABLE market_strategy.p_strategy_backtests
    ADD COLUMN IF NOT EXISTS total_return_rate NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS mdd NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS win_rate NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS trade_count INTEGER,
    ADD COLUMN IF NOT EXISTS max_consecutive_losses INTEGER;

