-- 1. 기존 prompt_key CHECK 제거
ALTER TABLE p_ai_prompt_versions
    DROP CONSTRAINT IF EXISTS p_ai_prompt_versions_prompt_key_check;

-- 2. 기존 데이터 마이그레이션
UPDATE p_ai_prompt_versions
SET prompt_key = 'STRATEGY_RISK_ANALYSIS'
WHERE prompt_key = 'RISK_ANALYSIS';

-- 3. 새로운 prompt_key CHECK 추가
ALTER TABLE p_ai_prompt_versions
    ADD CONSTRAINT p_ai_prompt_versions_prompt_key_check
    CHECK (
        prompt_key IN (
            'STRATEGY_RISK_ANALYSIS',
            'BACKTEST_ANALYSIS'
        )
    );