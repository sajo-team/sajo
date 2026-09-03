# Database migrations

`market-service` does not currently include Flyway or Liquibase. SQL files in this directory are not executed automatically, and there is no migration execution-history management.

## V44 execution order

1. Stop the `market-service` application for the target environment.
2. Back up the target PostgreSQL database and manually execute `V44__market_stock_price_daily_unique.sql` once.
3. Verify the REST daily duplicate cleanup and `uk_market_stock_price_daily_rest` Partial Unique Index.
4. Start or deploy `market-service`.

In local and development environments, `hibernate.ddl-auto: update` can add `accumulated_trade_amount` as a new column when the application starts before V44. Run V44 **before** starting the application so the migration can safely rename the legacy `trade_amount` column. V44 is written to be rerunnable, but it is still a manual operational procedure and must be recorded by the deployment operator.

## V52 execution order

Apply migrations manually in this order before starting the application: V44, then `V52__market_stock_code_unique.sql`.

Before V52, check for invalid existing data:

```sql
SELECT id, stock_code
FROM market_strategy.m_market_stocks
WHERE stock_code IS NULL;

SELECT stock_code, COUNT(*)
FROM market_strategy.m_market_stocks
GROUP BY stock_code
HAVING COUNT(*) > 1;
```

V52 applies `NOT NULL` and a unique `stock_code` index required for concurrent stock-master upserts. It is rerunnable and detects an equivalent unique constraint or index even when it uses another name. Null or duplicate codes deliberately stop the migration: automatic deletion or rewriting could silently discard a stock that is already referenced by market data. Resolve the data issue explicitly, rerun V52, and record the execution manually because Flyway/Liquibase history management is not available.
