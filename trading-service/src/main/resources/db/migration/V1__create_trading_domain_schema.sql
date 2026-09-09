-- =========================================================
-- Trading Service - Trading Domain Initial Flyway Migration
-- =========================================================

CREATE SCHEMA IF NOT EXISTS trading;


-- =========================================================
-- 1. AutoTrading
-- =========================================================

CREATE TABLE IF NOT EXISTS trading.p_auto_tradings (
   id UUID PRIMARY KEY,

   user_id UUID NOT NULL,
   strategy_id UUID NOT NULL,
   enabled BOOLEAN NOT NULL,

   created_at TIMESTAMPTZ NOT NULL,
   created_by UUID,
   updated_at TIMESTAMPTZ,
   updated_by UUID,
   deleted_at TIMESTAMPTZ,
   deleted_by UUID
);

-- 논리 삭제되지 않은 AutoTrading만
-- user + strategy 조합을 한 건으로 제한한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_auto_trading_active_user_strategy
    ON trading.p_auto_tradings (user_id, strategy_id)
    WHERE deleted_at IS NULL;


-- =========================================================
-- 2. TradingLimit
-- =========================================================

CREATE TABLE IF NOT EXISTS trading.p_trading_limits (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    daily_max_order_amount BIGINT NOT NULL,
    daily_max_order_count INTEGER NOT NULL,
    daily_loss_limit_rate NUMERIC(5, 2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_trading_limits_user_id UNIQUE (user_id)
);


-- =========================================================
-- 3. Order
-- =========================================================

CREATE TABLE IF NOT EXISTS trading.p_orders (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    auto_trading_id UUID NOT NULL,
    strategy_id UUID NOT NULL,
    signal_id UUID NOT NULL,

    stock_code VARCHAR(255) NOT NULL,
    order_type VARCHAR(255) NOT NULL,

    signal_price BIGINT NOT NULL,
    order_quantity INTEGER NOT NULL,

    filled_quantity INTEGER NOT NULL DEFAULT 0,
    remaining_quantity INTEGER NOT NULL,

    estimated_order_amount BIGINT NOT NULL,

    status VARCHAR(255) NOT NULL,

    broker_order_no VARCHAR(255),
    failure_code VARCHAR(255),
    failure_message VARCHAR(255),

    account_retry_count INTEGER NOT NULL,
    reconciliation_retry_count INTEGER NOT NULL,

    last_execution_checked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_orders_signal_id UNIQUE (signal_id)
);


-- =========================================================
-- 4. 기존 p_orders 보정
-- =========================================================

-- 기존 Hibernate ddl-auto:update 환경에서 이미 p_orders가 존재하지만
-- #133 신규 컬럼이 없는 DB까지 대응한다.

ALTER TABLE trading.p_orders
    ADD COLUMN IF NOT EXISTS filled_quantity INTEGER;

ALTER TABLE trading.p_orders
    ADD COLUMN IF NOT EXISTS remaining_quantity INTEGER;

ALTER TABLE trading.p_orders
    ADD COLUMN IF NOT EXISTS last_execution_checked_at TIMESTAMPTZ;


-- 기존 주문 데이터 백필
UPDATE trading.p_orders
SET filled_quantity = 0
WHERE filled_quantity IS NULL;

UPDATE trading.p_orders
SET remaining_quantity = order_quantity
WHERE remaining_quantity IS NULL;


-- Entity의 nullable=false와 일치시킨다.
ALTER TABLE trading.p_orders
    ALTER COLUMN filled_quantity SET NOT NULL;

ALTER TABLE trading.p_orders
    ALTER COLUMN remaining_quantity SET NOT NULL;


-- =========================================================
-- 5. Execution
-- =========================================================

CREATE TABLE IF NOT EXISTS trading.p_executions (
    id UUID PRIMARY KEY,

    order_id UUID NOT NULL,

    executed_quantity INTEGER NOT NULL,
    average_execution_price NUMERIC(19, 4) NOT NULL,
    total_execution_amount BIGINT NOT NULL,
    remaining_quantity INTEGER NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_executions_order_id UNIQUE (order_id)
);


-- =========================================================
-- 6. 조회 인덱스
-- =========================================================

-- 사용자 주문 조회
CREATE INDEX IF NOT EXISTS idx_orders_user_id
    ON trading.p_orders (user_id);

-- 체결 polling:
-- status IN (...) AND last_execution_checked_at < cutoff
CREATE INDEX IF NOT EXISTS idx_orders_execution_polling
    ON trading.p_orders (status, last_execution_checked_at)
    WHERE deleted_at IS NULL;

-- AutoTrading 기준 주문 조회 가능성을 고려
CREATE INDEX IF NOT EXISTS idx_orders_auto_trading_id
    ON trading.p_orders (auto_trading_id);