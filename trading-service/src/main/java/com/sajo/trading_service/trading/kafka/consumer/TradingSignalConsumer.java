package com.sajo.trading_service.trading.kafka.consumer;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.service.command.TradingSignalCommandService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class TradingSignalConsumer {

    private final TradingSignalCommandService tradingSignalCommandService;
    private final Validator validator;

    @KafkaListener(topics = "trading.signal.generated")
    public void consume(TradingSignalGeneratedEvent event) {

        Set<ConstraintViolation<TradingSignalGeneratedEvent>> violations =
                validator.validate(event);

        if (!violations.isEmpty()) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_TRADING_SIGNAL
            );
        }

        tradingSignalCommandService.processSignal(event);
    }
}