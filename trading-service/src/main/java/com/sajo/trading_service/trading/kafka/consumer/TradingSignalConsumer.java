package com.sajo.trading_service.trading.kafka.consumer;

import com.sajo.common.code.ErrorCode;
import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.service.command.TradingSignalCommandService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingSignalConsumer {

    private static final Set<ErrorCode> SKIPPABLE_ERROR_CODES = Set.of(
            TradingErrorCode.AUTO_TRADING_NOT_FOUND,
            TradingErrorCode.AUTO_TRADING_DISABLED,
            TradingErrorCode.ORDER_QUANTITY_NOT_AVAILABLE,
            TradingErrorCode.DAILY_ORDER_COUNT_LIMIT_EXCEEDED,
            TradingErrorCode.DAILY_ORDER_AMOUNT_LIMIT_EXCEEDED,
            TradingErrorCode.ORDER_QUANTITY_OUT_OF_RANGE
    );

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

        try {
            tradingSignalCommandService.processSignal(event);
        } catch (BusinessException e) {
            if (SKIPPABLE_ERROR_CODES.contains(e.getErrorCode())) {
                log.info(
                        "매매 Signal 주문 생성 스킵. signalId={}, errorCode={}",
                        event.payload().signalId(),
                        e.getErrorCode()
                );
                return;
            }

            throw e;
        }

    }
}