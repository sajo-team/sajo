package com.sajo.trading_service.ai_risk.exception;

import com.sajo.trading_service.ai_risk.domain.AiValidationType;
import lombok.Getter;

@Getter
public class AiResponseValidationException extends RuntimeException {

    private final AiValidationType validationType;

    public AiResponseValidationException(
            AiValidationType validationType,
            String message
    ){
        super(message);
        this.validationType = validationType;
    }
}
