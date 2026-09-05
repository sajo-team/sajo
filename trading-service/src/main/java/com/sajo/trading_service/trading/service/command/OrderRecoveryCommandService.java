package com.sajo.trading_service.trading.service.command;

import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
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

    private static final long STALE_MINUTES = 1L;

    private final OrderCommandRepository orderCommandRepository;
    private final KisOrderCommandService kisOrderCommandService;
    private final OrderStatusCommandService orderStatusCommandService;

    public void recoverRequestedOrders() {

        Instant cutoff =
                Instant.now().minus(STALE_MINUTES, ChronoUnit.MINUTES);

        List<UUID> orderIds =
                orderCommandRepository.findStaleRequestedOrderIds(cutoff);

        for (UUID orderId : orderIds) {
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

    public void recoverProcessingOrders() {

        Instant cutoff =
                Instant.now().minus(STALE_MINUTES, ChronoUnit.MINUTES);

        List<UUID> orderIds =
                orderCommandRepository.findStaleProcessingOrderIds(cutoff);

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