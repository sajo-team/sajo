-- Manual migration: Flyway/Liquibase is not configured, so this file is NOT executed automatically.
DO $$
DECLARE
    stock_code_attnum SMALLINT;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM market_strategy.m_market_stocks
        WHERE stock_code IS NULL
    ) THEN
        RAISE EXCEPTION 'V52 cannot apply: m_market_stocks.stock_code contains NULL values';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM market_strategy.m_market_stocks
        GROUP BY stock_code
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V52 cannot apply: m_market_stocks.stock_code contains duplicate values';
    END IF;

    ALTER TABLE market_strategy.m_market_stocks
        ALTER COLUMN stock_code SET NOT NULL;

    SELECT attnum
    INTO stock_code_attnum
    FROM pg_attribute
    WHERE attrelid = 'market_strategy.m_market_stocks'::regclass
      AND attname = 'stock_code'
      AND NOT attisdropped;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index index_definition
        WHERE index_definition.indrelid = 'market_strategy.m_market_stocks'::regclass
          AND index_definition.indisunique
          AND index_definition.indpred IS NULL
          AND index_definition.indnkeyatts = 1
          AND index_definition.indkey::smallint[] = ARRAY[stock_code_attnum]
    ) THEN
        CREATE UNIQUE INDEX uk_market_stock_stock_code
            ON market_strategy.m_market_stocks (stock_code);
    END IF;
END $$;
