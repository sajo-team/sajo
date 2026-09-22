-- #315: 호가단위(틱) 스냅으로 signalPrice와 실제 KIS 접수가가 달라질 수 있어,
-- 실제로 KIS에 접수한 가격을 별도 컬럼에 기록한다.
-- 기존 주문 데이터는 이 컬럼이 없던 시점에 생성됐으므로 NULL로 남으며,
-- 조회/재조정 로직은 이 값이 NULL인 경우 signalPrice를 다시 스냅해 하위 호환을 유지한다.
ALTER TABLE trading.p_orders
    ADD COLUMN IF NOT EXISTS executed_order_price BIGINT;
