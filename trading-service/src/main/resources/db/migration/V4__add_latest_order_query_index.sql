-- =========================================================
-- 최근 주문 조회 성능 개선
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_orders_auto_trading_latest
    ON trading.p_orders (
                         auto_trading_id,
                         created_at DESC
        )
    WHERE deleted_at IS NULL;