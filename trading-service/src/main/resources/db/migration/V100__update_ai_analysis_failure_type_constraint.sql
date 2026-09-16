ALTER TABLE trading.p_ai_risk_analyses
DROP CONSTRAINT IF EXISTS p_ai_risk_analyses_failure_type_check;

ALTER TABLE trading.p_ai_risk_analyses
ADD CONSTRAINT p_ai_risk_analyses_failure_type_check
CHECK (
    failure_type IS NULL
    OR failure_type IN (
        'LLM_API_ERROR',
        'RESPONSE_PARSE_ERROR',
        'VALIDATION_ERROR',
        'INTERNAL_ERROR',
        'PROMPT_NOT_FOUND'
    )
);