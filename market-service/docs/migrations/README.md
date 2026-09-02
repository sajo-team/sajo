# Database migrations

`market-service` does not currently include Flyway or Liquibase. SQL files in this directory are not executed automatically.

Apply `V44__market_stock_price_daily_unique.sql` manually to the production PostgreSQL database before deploying the historical daily-price collection feature.
