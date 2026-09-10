-- Manual migration: this project does not execute Flyway/Liquibase migrations automatically.
--
-- eps/bps are unused going forward: the V104 quarterly-snapshot upsert (MarketStockIndicatorWriter)
-- only ever writes them as literal NULL, and no consumer (Strategy's internal contract, /indicators,
-- /indicators/history) reads them. /quote's eps/bps are unrelated and unaffected — they come live
-- from the KIS current-price API, not from this table.
--
-- IMPORTANT: this is NOT true for every row. Before the #185 quarterly-financial-ratio refactor,
-- the writer stored the real KIS current-price eps/bps for every legacy (reference_date-based) row
-- (insert ... eps, bps ... on conflict ... do update set eps = coalesce(excluded.eps, ...), bps =
-- coalesce(excluded.bps, ...)). Any environment that ran the indicator scheduler before that refactor
-- can have legacy rows with real, non-null eps/bps values. KIS's current-price API cannot return a
-- past point-in-time eps/bps, so this data cannot be re-collected once dropped.
--
-- This migration refuses to run while any row still has a non-null eps or bps, so it cannot silently
-- delete real historical data. If the check fails, the team must explicitly decide (and record) whether
-- to archive those values first (e.g. export to a backup table) or accept the loss, then rerun.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM market_strategy.m_market_stocks_indicator
        WHERE eps IS NOT NULL OR bps IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'V105 cannot apply: rows with non-null eps/bps exist — back up or confirm loss before dropping';
    END IF;
END $$;

ALTER TABLE market_strategy.m_market_stocks_indicator
    DROP COLUMN IF EXISTS eps,
    DROP COLUMN IF EXISTS bps;
