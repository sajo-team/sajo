-- =========================================================
-- Trading Service - AI Risk Domain Initial Flyway Migration
-- =========================================================

CREATE SCHEMA IF NOT EXISTS trading;


-- =========================================================
-- 1. AI Risk Analysis
-- =========================================================

CREATE TABLE IF NOT EXISTS trading.p_ai_risk_analyses (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    strategy_id UUID NOT NULL,
    backtest_id UUID NOT NULL,

    status VARCHAR(20) NOT NULL,
    risk_level VARCHAR(10),

    summary TEXT,
    risk_factors JSONB,
    reasoning TEXT,
    recommendations JSONB,

    failure_type VARCHAR(50),
    failure_message TEXT,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);

-- 동일 사용자/전략/백테스트에 대해
-- 하나의 PENDING 분석만 존재하도록 제한한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_risk_analysis_pending
    ON trading.p_ai_risk_analyses (user_id, strategy_id, backtest_id)
    WHERE status = 'PENDING';


-- =========================================================
-- 2. AI Prompt Version
-- =========================================================

CREATE TABLE IF NOT EXISTS trading.p_ai_prompt_versions (
    id UUID PRIMARY KEY,

    prompt_key VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    prompt_content TEXT NOT NULL,
    change_summary TEXT,

    status VARCHAR(20) NOT NULL,

    deployed_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_ai_prompt_key_version
        UNIQUE (prompt_key, version)
);

-- 동일 prompt_key에는 하나의 ACTIVE 프롬프트만 존재하도록 제한한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_prompt_active
    ON trading.p_ai_prompt_versions (prompt_key)
    WHERE status = 'ACTIVE';