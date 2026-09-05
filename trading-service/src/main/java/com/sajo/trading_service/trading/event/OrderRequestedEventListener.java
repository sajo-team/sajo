package com.sajo.trading_service.trading.event;

import com.sajo.trading_service.trading.service.command.KisOrderCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRequestedEventListener {

    private final KisOrderCommandService kisOrderCommandService;

    @Async("kisOrderExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT // 현재 트랜잭션이 성공적으로 Commit된 뒤에 실행
    )
    public void handle(
            OrderRequestedEvent event
    ) {
        try {
            kisOrderCommandService.executeOrder(
                    event.orderId()
            );

        } catch (RuntimeException e) {
            log.error(
                    "Order 생성 후 KIS 주문 실행 실패. orderId={}",
                    event.orderId(),
                    e
            );
        }
    }
}