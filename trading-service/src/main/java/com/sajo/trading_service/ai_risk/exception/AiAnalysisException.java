package com.sajo.trading_service.ai_risk.exception;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import lombok.Getter;

@Getter
public class AiAnalysisException extends RuntimeException {

    private final AiAnalysisFailureType failureType;
    private final AiPromptKey promptKey;
    private final String promptVersion;
    private final String promptContent;
    private final String model;
    private final long latencyMs;

    public AiAnalysisException(
            AiAnalysisFailureType failureType,
            String message,
            AiPromptKey promptKey,
            String promptVersion,
            String promptContent,
            String model,
            long latencyMs,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
        this.promptKey = promptKey;
        this.promptVersion = promptVersion;
        this.promptContent = promptContent;
        this.model = model;
        this.latencyMs = latencyMs;
    }
}
