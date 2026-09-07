package com.sajo.trading_service.trading.scheduler;

import com.sajo.trading_service.trading.service.command.OrderRecoveryCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRecoveryScheduler {

    private final OrderRecoveryCommandService orderRecoveryCommandService;

    @Scheduled(fixedDelay = 10_000)
    public void recoverStaleOrders() {
        orderRecoveryCommandService.recoverRequestedOrders();
        orderRecoveryCommandService.recoverProcessingOrders();
    }
}