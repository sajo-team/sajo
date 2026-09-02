package com.sajo.trading_service.ai_risk.domain;

public enum AiAnalysisFailureType {
    LLM_API_ERROR, //GPT API 호출 실패
    RESPONSE_PARSE_ERROR, //GPT 응답 파싱 실패
    VALIDATION_ERROR // GPT 응답 검증 실패
}
