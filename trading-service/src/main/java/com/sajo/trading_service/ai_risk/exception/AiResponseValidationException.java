package com.sajo.trading_service.ai_risk.exception;

public class AiResponseValidationException extends RuntimeException {

    public AiResponseValidationException(String message){
        super(message);
    }
}
