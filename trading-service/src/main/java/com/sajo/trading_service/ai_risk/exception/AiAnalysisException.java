package com.sajo.trading_service.ai_risk.exception;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;


public class AiAnalysisException extends RuntimeException {

    private final AiAnalysisFailureType failureType;

    public AiAnalysisException(
            AiAnalysisFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
    }

    public AiAnalysisFailureType getFailureType(){
        return failureType;
    }
}
