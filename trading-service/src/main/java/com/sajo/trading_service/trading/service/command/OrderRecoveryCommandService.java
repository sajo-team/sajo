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

    // KIS 주문 요청 후 응답 지연이나 상태 반영 실패로 PROCESSING 상태가 장시간 유지되는 경우,
    // 5분 이후 주문 조회 기반 reconciliation 대상으로 처리한다.
    private static final long PROCESSING_STALE_MINUTES = 5L;
    private static final long TIMEOUT_RECONCILIATION_MINUTES = 1L;
    private static final long EXECUTION_INQUIRY_INTERVAL_SECONDS = 30L;

    private final OrderQueryRepository orderQueryRepository;
    private final OrderStatusCommandService orderStatusCommandService;
    private final OrderRecoveryExecutor orderRecoveryExecutor;
    private final KisOrderReconciliationService kisOrderReconciliationService;
    private final KisOrderExecutionService kisOrderExecutionService;

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

    public void recoverExecutions() {

        Instant cutoff =
                Instant.now().minus(
                        EXECUTION_INQUIRY_INTERVAL_SECONDS,
                        ChronoUnit.SECONDS
                );

        List<UUID> orderIds =
                orderQueryRepository.findExecutionTargetOrderIds(cutoff);

        for (UUID orderId : orderIds) {
            try {
                kisOrderExecutionService.processExecution(orderId);

            } catch (RuntimeException e) {
                log.error(
                        "주문 체결 조회 처리 실패. orderId={}",
                        orderId,
                        e
                );
            }
        }
    }
}