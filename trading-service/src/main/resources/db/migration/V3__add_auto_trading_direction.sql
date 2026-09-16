-- =========================================================
-- AutoTrading 주문 방향 설정 추가
-- =========================================================

ALTER TABLE trading.p_auto_tradings
    ADD COLUMN IF NOT EXISTS direction VARCHAR(20);

-- 기존 AutoTrading은 기존 동작과 동일하게
-- BUY / SELL 모두 허용하도록 BOTH로 백필
UPDATE trading.p_auto_tradings
SET direction = 'BOTH'
WHERE direction IS NULL;

ALTER TABLE trading.p_auto_tradings
    ALTER COLUMN direction SET NOT NULL;