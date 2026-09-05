package com.sajo.trading_service.trading.service.command;

import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRecoveryCommandService {

    private static final long STALE_MINUTES = 5L;

    private final OrderQueryRepository orderQueryRepository;
    private final OrderStatusCommandService orderStatusCommandService;
    private final OrderRecoveryExecutor orderRecoveryExecutor;

    public void recoverRequestedOrders() {

        Instant cutoff =
                Instant.now().minus(STALE_MINUTES, ChronoUnit.MINUTES);

        List<UUID> orderIds =
                orderQueryRepository.findStaleRequestedOrderIds(cutoff);

        for (UUID orderId : orderIds) {
            orderRecoveryExecutor.execute(orderId);
        }
    }

    public void recoverProcessingOrders() {

        Instant cutoff =
                Instant.now().minus(STALE_MINUTES, ChronoUnit.MINUTES);

        List<UUID> orderIds =
                orderQueryRepository.findStaleProcessingOrderIds(cutoff);

        for (UUID orderId : orderIds) {
            try {
                orderStatusCommandService.timeout(
                        orderId,
                        "ORDER_PROCESSING_TIMEOUT",
                        "주문 처리 결과를 확인할 수 없습니다."
                );

            } catch (RuntimeException e) {
                log.error(
                        "PROCESSING 주문 TIMEOUT 전환 실패. orderId={}",
                        orderId,
                        e
                );
            }
        }
    }
}