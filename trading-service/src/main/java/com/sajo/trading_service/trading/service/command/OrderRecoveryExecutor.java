package com.sajo.trading_service.trading.service.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRecoveryExecutor {

    private final KisOrderCommandService kisOrderCommandService;

    @Async("kisOrderExecutor")
    public void execute(UUID orderId) {
        try {
            kisOrderCommandService.executeOrder(orderId);

        } catch (RuntimeException e) {
            log.error(
                    "REQUESTED 주문 복구 실행 실패. orderId={}",
                    orderId,
                    e
            );
        }
    }
}