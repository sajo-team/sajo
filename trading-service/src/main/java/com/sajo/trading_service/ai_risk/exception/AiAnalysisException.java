package com.sajo.trading_service.ai_risk.exception;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import lombok.Getter;

@Getter
public class AiAnalysisException extends RuntimeException {

    private final AiAnalysisFailureType failureType;
    private final String promptVersion;
    private final String promptContent;
    private final String model;
    private final long latencyMs;

    public AiAnalysisException(
            AiAnalysisFailureType failureType,
            String message,
            String promptVersion,
            String promptContent,
            String model,
            long latencyMs,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
        this.promptVersion = promptVersion;
        this.promptContent = promptContent;
        this.model = model;
        this.latencyMs = latencyMs;
    }
}
