package com.sajo.trading_service.ai_risk.exception;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import lombok.Getter;

@Getter
public class AiResponseParseException extends AiAnalysisException {

    private final String rawResponse;

    public AiResponseParseException(
            String message,
            String rawResponse,
            Throwable cause
    ) {
        super(
                AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                message,
                cause
        );
        this.rawResponse = rawResponse;
    }
}
