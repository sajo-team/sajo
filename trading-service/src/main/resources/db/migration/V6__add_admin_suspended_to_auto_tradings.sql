ALTER TABLE trading.p_auto_tradings
    ADD COLUMN admin_suspended BOOLEAN NOT NULL DEFAULT FALSE;