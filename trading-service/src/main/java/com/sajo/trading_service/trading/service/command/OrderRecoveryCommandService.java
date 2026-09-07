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

    private static final long REQUESTED_STALE_MINUTES = 5L;
    private static final long ACCOUNT_RETRY_STALE_SECONDS = 30L;
    private static final long PROCESSING_STALE_MINUTES = 5L;
    private static final long TIMEOUT_RECONCILIATION_MINUTES = 1L;

    private final OrderQueryRepository orderQueryRepository;
    private final OrderStatusCommandService orderStatusCommandService;
    private final OrderRecoveryExecutor orderRecoveryExecutor;
    private final KisOrderReconciliationService kisOrderReconciliationService;

    public void recoverRequestedOrders() {

        Instant normalCutoff =
                Instant.now().minus(
                        REQUESTED_STALE_MINUTES,
                        ChronoUnit.MINUTES
                );

        Instant retryCutoff =
                Instant.now().minus(
                        ACCOUNT_RETRY_STALE_SECONDS,
                        ChronoUnit.SECONDS
                );

        List<UUID> orderIds =
                orderQueryRepository.findStaleRequestedOrderIds(
                        normalCutoff,
                        retryCutoff
                );

        for (UUID orderId : orderIds) {
            orderRecoveryExecutor.execute(orderId);
        }
    }

    public void recoverProcessingOrders() {

        Instant cutoff =
                Instant.now().minus(
                        PROCESSING_STALE_MINUTES,
                        ChronoUnit.MINUTES
                );

        List<UUID> orderIds =
                orderQueryRepository.findStaleProcessingOrderIds(cutoff);

        for (UUID orderId : orderIds) {
            try {
                kisOrderReconciliationService.reconcile(orderId);

            } catch (RuntimeException e) {
                log.error(
                        "PROCESSING 주문 상태 보정 실패. orderId={}",
                        orderId,
                        e
                );
            }
        }
    }

    public void recoverTimeoutOrders() {

        Instant cutoff =
                Instant.now().minus(
                        TIMEOUT_RECONCILIATION_MINUTES,
                        ChronoUnit.MINUTES
                );

        List<UUID> orderIds =
                orderQueryRepository.findStaleTimeoutOrderIds(cutoff);

        for (UUID orderId : orderIds) {
            try {
                kisOrderReconciliationService.reconcile(orderId);
            } catch (RuntimeException e) {
                log.error(
                        "TIMEOUT 주문 보정 실패. orderId={}",
                        orderId,
                        e
                );
            }
        }
    }
}