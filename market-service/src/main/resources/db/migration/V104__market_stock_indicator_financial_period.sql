-- Manual migration: this project does not execute Flyway/Liquibase migrations automatically.
ALTER TABLE market_strategy.m_market_stocks_indicator
    ALTER COLUMN reference_date DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS valuation_fetched_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS financial_period_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS financial_reference_year_month VARCHAR(7),
    ADD COLUMN IF NOT EXISTS financial_fetched_at TIMESTAMP WITH TIME ZONE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM market_strategy.m_market_stocks_indicator
        WHERE financial_period_type IS NOT NULL
          AND financial_reference_year_month IS NOT NULL
        GROUP BY stock_id, financial_period_type, financial_reference_year_month
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V104 cannot apply: duplicate financial indicator periods exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_market_stock_indicator_financial_period
    ON market_strategy.m_market_stocks_indicator
        (stock_id, financial_period_type, financial_reference_year_month)
    WHERE financial_period_type IS NOT NULL
      AND financial_reference_year_month IS NOT NULL;
