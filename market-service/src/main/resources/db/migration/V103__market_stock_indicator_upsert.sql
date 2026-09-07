-- Manual migration: Flyway/Liquibase is not configured, so this file is NOT executed automatically.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM market_strategy.m_market_stocks_indicator
        WHERE stock_id IS NULL OR reference_date IS NULL
    ) THEN
        RAISE EXCEPTION 'V103 cannot apply: m_market_stocks_indicator contains NULL stock_id or reference_date';
    END IF;

    IF EXISTS (
        SELECT 1 FROM market_strategy.m_market_stocks_indicator
        GROUP BY stock_id, reference_date
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V103 cannot apply: m_market_stocks_indicator contains duplicate stock_id and reference_date';
    END IF;

    ALTER TABLE market_strategy.m_market_stocks_indicator
        ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index index_definition
        WHERE index_definition.indrelid = 'market_strategy.m_market_stocks_indicator'::regclass
          AND index_definition.indisunique
          AND index_definition.indpred IS NULL
          AND index_definition.indnkeyatts = 2
          AND index_definition.indkey::smallint[] = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'market_strategy.m_market_stocks_indicator'::regclass AND attname = 'stock_id' AND NOT attisdropped),
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'market_strategy.m_market_stocks_indicator'::regclass AND attname = 'reference_date' AND NOT attisdropped)
          ]
    ) THEN
        CREATE UNIQUE INDEX uk_market_stock_indicator_stock_reference_date
            ON market_strategy.m_market_stocks_indicator (stock_id, reference_date);
    END IF;
END $$;
