package com.sajo.trading_service.trading.kafka.consumer;

import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.service.command.TradingSignalCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradingSignalConsumer {

    private final TradingSignalCommandService tradingSignalCommandService;

    @KafkaListener(topics = "trading.signal.generated")
    public void consume(TradingSignalGeneratedEvent event) {
        tradingSignalCommandService.processSignal(event);
    }
}
