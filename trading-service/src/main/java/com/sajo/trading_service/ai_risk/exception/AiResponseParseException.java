package com.sajo.trading_service.ai_risk.exception;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import lombok.Getter;

@Getter
public class AiResponseParseException extends AiAnalysisException {

    private final String rawResponse;

    public AiResponseParseException(
            String message,
            String rawResponse,
            String promptVersion,
            String promptContent,
            String model,
            long latencyMs,
            Throwable cause
    ) {
        super(
                AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                message,
                promptVersion,
                promptContent,
                model,
                latencyMs,
                cause
        );

        this.rawResponse = rawResponse;
    }
}
