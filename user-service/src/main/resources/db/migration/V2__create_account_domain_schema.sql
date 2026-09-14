-- =========================================================
-- User Service - Account Domain Initial Flyway Migration
-- =========================================================

CREATE SCHEMA IF NOT EXISTS user_account;


-- =========================================================
-- 1. Account
-- =========================================================

CREATE TABLE IF NOT EXISTS user_account.p_accounts (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    app_key TEXT NOT NULL,
    secret_key TEXT NOT NULL,
    account_no TEXT NOT NULL,
    account_no_hash VARCHAR(255) NOT NULL,
    account_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,

    -- 소프트 삭제된 row끼리 유니크 제약이 서로 겹치지 않게 하는 컬럼
    -- (Account.softDelete: 활성 상태는 고정값 0, 삭제 상태는 자기 id로 교체)
    unique_column UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_account_user_id UNIQUE (user_id, unique_column),
    CONSTRAINT uq_account_no_hash UNIQUE (account_no_hash, unique_column)
);


-- =========================================================
-- 2. KisTokenLog
-- =========================================================

CREATE TABLE IF NOT EXISTS user_account.p_kis_token_logs (
    id UUID PRIMARY KEY,

    -- 계좌 생성 검증(appKey/secretKey) 실패 시엔 아직 Account가 없어 null일 수 있다
    account_id UUID,
    user_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    token_type VARCHAR(255) NOT NULL,
    error_code VARCHAR(255),
    error_message VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID
);
