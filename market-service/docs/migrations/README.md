# Database migrations

`market-service` uses Flyway (adopted in #218) to manage `market_strategy` schema migrations. Flyway
runs automatically on application startup against `spring.flyway.schemas: market_strategy`
(see `application.yaml`). New schema changes from now on should be added as a new versioned file in
this directory (`V<next>__description.sql`) instead of the old "manual psql" convention.

## Baseline (V44–V105)

V44, V52, V53, V103, V104 and V105 below predate Flyway and were already applied by hand (psql) to
every real environment before this adoption. `application.yaml` sets:

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 105
```

so on any schema that already has these changes applied (which today means every real schema —
prod and any developer's already-bootstrapped local DB), Flyway records a baseline row at version
105 on first boot and does **not** re-execute V44–V105. Only new migrations above V105 (e.g. V106+)
are actually run by Flyway. The execution notes for V44–V105 further down are kept for history —
they describe how each file was originally applied by hand, not something Flyway will redo.

**Important limitation — this does not cover a truly empty schema.** `baseline-on-migrate` only
triggers for a *non-empty* schema. Against a brand-new schema that has no tables at all (a fresh
local DB before its first boot, or the throwaway schema CI creates with `CREATE SCHEMA IF NOT
EXISTS`), Flyway sees nothing to baseline and instead tries to run V44 onward for real — which fails
immediately, since V44 issues `ALTER TABLE` against tables that don't exist yet (they're normally
created by Hibernate `ddl-auto`, not by these historical files). This is the same pre-existing gap the
"Bootstrapping a brand-new database" runbook below was already written for; see the updated steps
there for how to get a fresh schema to a state where Flyway can take over. market-service's test suite
avoids this entirely by disabling Flyway for the H2-backed `local`/`test` profiles
(`src/test/resources/application-{local,test}.yaml`), since those profiles rebuild their schema from
scratch via `ddl-auto: create-drop` on every run anyway.

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

## V103 execution order

Apply V44, V52, then `V103__market_stock_indicator_upsert.sql` manually before starting the application. V103 verifies that `stock_id` and `reference_date` have no null or duplicate values, adds `updated_at` when missing, and ensures a unique `stock_id + reference_date` key required by the indicator upsert. It does not delete or rewrite existing data. Record the manual execution because Flyway/Liquibase execution history is not available.

## Scheduler KIS rate-limit operation

## V104 execution order

Apply V44, V52, V103, then `V104__market_stock_indicator_financial_period.sql` manually before deploying the new indicator collector. V104 leaves existing rows unchanged, makes the legacy `reference_date` nullable, and adds separate valuation receipt time and quarterly financial-period metadata. Existing rows are not assigned fabricated periods or receipt times. Verify duplicate non-null `(stock_id, financial_period_type, financial_reference_year_month)` values before applying. The SQL is not run automatically because Flyway/Liquibase is not configured; record its execution separately.

## V105 execution order

Apply V44, V52, V103, V104, then `V105__market_stock_indicator_drop_eps_bps.sql` manually. V105 drops the `eps`/`bps` columns from `m_market_stocks_indicator`.

**This is not a safe blanket drop of always-null data.** The current (post-#185) quarterly upsert (`MarketStockIndicatorWriter`) only ever writes `eps`/`bps` as literal `NULL`, and no consumer (Strategy's internal contract, `/indicators`, `/indicators/history`) reads them — but before the #185 quarterly-financial-ratio refactor, the writer stored the real KIS current-price `eps`/`bps` for every legacy (`reference_date`-based) row. Any environment that ran the indicator scheduler before that refactor can have legacy rows with real, non-null `eps`/`bps` values, and KIS's current-price API cannot return a past point-in-time `eps`/`bps`, so this data cannot be re-collected once dropped.

V105 therefore checks for any row with a non-null `eps` or `bps` and refuses to run (raises an exception) if one exists, mirroring V104's duplicate-check guard. If it refuses, the team must explicitly decide — and record the decision — whether to archive those values (e.g. export to a backup table) before rerunning, or accept the loss.

This does **not** affect `/quote`'s `eps`/`bps`, which are served live from the KIS current-price API and are unrelated to this table.

Deploy the updated `market-service` (with the `eps`/`bps` fields removed from the `MarketStockIndicator` entity) only after V105 has been applied, so `hibernate.ddl-auto: validate` does not fail against columns the entity no longer declares. A brand-new (empty) database bootstrapped after this change never creates these columns in the first place, so V105 is only relevant to already-existing databases.

The daily-price and indicator schedulers use the same JVM-local KIS request limiter. Their default cron times are 16:10 and 16:20 (Asia/Seoul), but a long daily run can overlap the indicator run; the shared limiter therefore spaces their combined KIS calls by at least 500 ms (at most two requests per second).

Spring's default scheduler uses a single scheduler thread, so scheduled jobs in one application instance run sequentially. This does not provide a global guarantee: multiple application instances each have their own limiter and can exceed the App Key limit together.

Before enabling either scheduler in an environment, operate only one scheduler-active application instance for the system App Key and avoid overlap with other batches that use the same App Key. The limiter is not distributed, so multiple instances or separate applications can still exceed the KIS App Key limit. A distributed scheduler lock and distributed rate limiter remain follow-up work.

Each scheduler obtains the system user's KIS credentials once per run and reuses them for the batch. If the token's remaining lifetime is shorter than the full batch duration, later stocks can fail after token expiry; token lifetime and batch size must be monitored operationally.

## Bootstrapping a brand-new (empty) production database

The current entities already include everything V52, V53, and V103 were written to add
(`stock_code` unique/not-null, the backtest result columns, and the
`stock_id + reference_date` unique constraint on `m_market_stocks_indicator`). Only V44
adds something Hibernate cannot generate from annotations: the **partial** unique index
`uk_market_stock_price_daily_rest` (`WHERE time IS NULL AND source = 'REST'`).

Verified against a schema generated from the current entities (2026-09-08):

0. **With Flyway now enabled by default, step 1 will fail unless Flyway is turned off for
   this bootstrap boot.** Flyway runs before Hibernate creates any tables, so against a
   completely empty schema it will try to execute V44 for real (`ALTER TABLE
   market_strategy.m_market_stocks_price ...`) and fail immediately because that table
   doesn't exist yet. Stage `SPRING_FLYWAY_ENABLED: "false"` alongside the
   `SPRING_JPA_HIBERNATE_DDL_AUTO: update` override in step 1, and keep it set through
   step 2 as well.
1. Boot `market-service` (and `trading-service`, which has no migration files at all)
   **once** with `hibernate.ddl-auto: update` (and Flyway disabled, per step 0) against
   the empty schema. Since there are no existing tables, this behaves identically to a
   manual `CREATE TABLE` script and only creates what's missing — it does not run any
   risky `ALTER` on real data. In practice this is done via a temporary
   `SPRING_JPA_HIBERNATE_DDL_AUTO: update` environment override already staged in
   `docker-compose.prod.yaml` (rather than editing `sajo-config-repo`, which doesn't set
   `ddl-auto` at all) — remove those overrides and redeploy once step 2 below is
   confirmed healthy.
2. After a healthy boot, run **V44 only**, by hand (psql), same as before Flyway —
   Flyway is still disabled at this point (step 0), so it will not pick this up on its
   own. It is safe and rerunnable (confirmed by running it twice back-to-back).
3. Do **not** run V52 or V103 by hand against a freshly bootstrapped schema:
   - V52 will not error, but its "does an equivalent index already exist" check never
     matches (see bug note below), so it silently creates a **duplicate** unique index
     on `stock_code` alongside the one Hibernate already created from the entity
     annotation.
   - V103 will **fail with a hard error**
     (`relation "uk_market_stock_indicator_stock_reference_date" already exists"`)
     because the entity's `@UniqueConstraint` already created an index under that exact
     name.
   - V53 is a harmless no-op (all its `ADD COLUMN IF NOT EXISTS` guards correctly skip),
     but there is no reason to run it either.
4. Switch `ddl-auto` back to `validate` and remove the `SPRING_FLYWAY_ENABLED: "false"`
   override (i.e. let it default back to enabled) for both services immediately after
   step 2, and never set `ddl-auto` back to `update` in production again. The schema is
   now non-empty and already contains everything V44–V105 would have added, so the next
   boot's Flyway run baselines it at V105 (per `baseline-version: 105` in
   `application.yaml`) instead of trying to re-run them.

### Known bug: V52 / V103 existence checks (non-blocking, tracked separately)

Both scripts detect an existing matching index with:

```sql
index_definition.indkey::smallint[] = ARRAY[...]
```

Casting `pg_index.indkey` (an `int2vector`) to `smallint[]` produces an array with a
`[0:1]` lower bound, which never equals a normal `ARRAY[...]` literal (`[1:n]` bound) —
so the "already exists" branch can never be taken. A working replacement, verified
locally:

```sql
indkey::text = array_to_string(ARRAY[...], ' ')
```

This is left as a follow-up fix (see issue tracker) rather than being edited in place,
since these files represent an already-applied migration history.
