# Database migrations

`market-service` does not currently include Flyway or Liquibase. SQL files in this directory are not executed automatically, and there is no migration execution-history management.

## V44 execution order

1. Stop the `market-service` application for the target environment.
2. Back up the target PostgreSQL database and manually execute `V44__market_stock_price_daily_unique.sql` once.
3. Verify the REST daily duplicate cleanup and `uk_market_stock_price_daily_rest` Partial Unique Index.
4. Start or deploy `market-service`.

In local and development environments, `hibernate.ddl-auto: update` can add `accumulated_trade_amount` as a new column when the application starts before V44. Run V44 **before** starting the application so the migration can safely rename the legacy `trade_amount` column. V44 is written to be rerunnable, but it is still a manual operational procedure and must be recorded by the deployment operator.
