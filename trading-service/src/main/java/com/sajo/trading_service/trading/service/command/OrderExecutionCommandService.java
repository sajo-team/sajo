package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.Execution;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.ExecutionCommandRepository;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderExecutionCommandService {

    private final OrderCommandRepository orderCommandRepository;
    private final ExecutionCommandRepository executionCommandRepository;

    @Transactional
    public void applyExecution(
            UUID orderId,
            int totalFilledQuantity,
            int remainingQuantity,
            BigDecimal averageExecutionPrice,
            long totalExecutionAmount
    ) {
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        int newlyFilledQuantity =
                order.applyFill(
                        totalFilledQuantity,
                        remainingQuantity
                );

        /*
         * 체결 수량이 증가하지 않았다면
         * 동일한 누적 체결 결과를 다시 조회한 것이므로
         * Execution도 중복 갱신하지 않는다.
         */
        if (newlyFilledQuantity == 0) {
            return;
        }

        Execution execution =
                executionCommandRepository.findByOrderId(orderId)
                        .orElse(null);

        if (execution == null) {
            executionCommandRepository.save(
                    Execution.create(
                            orderId,
                            totalFilledQuantity,
                            averageExecutionPrice,
                            totalExecutionAmount,
                            remainingQuantity
                    )
            );
            return;
        }

        execution.update(
                totalFilledQuantity,
                averageExecutionPrice,
                totalExecutionAmount,
                remainingQuantity
        );
    }

    @Transactional
    public void applyCancellation(
            UUID orderId,
            int totalFilledQuantity,
            int remainingQuantity,
            BigDecimal averageExecutionPrice,
            long totalExecutionAmount
    ) {
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        order.cancel(
                totalFilledQuantity,
                remainingQuantity
        );

        /*
         * 체결 없이 전체 취소된 경우에는
         * Execution을 생성하지 않는다.
         */
        if (totalFilledQuantity == 0) {
            return;
        }

        Execution execution =
                executionCommandRepository.findByOrderId(orderId)
                        .orElse(null);

        if (execution == null) {
            executionCommandRepository.save(
                    Execution.create(
                            orderId,
                            totalFilledQuantity,
                            averageExecutionPrice,
                            totalExecutionAmount,
                            remainingQuantity
                    )
            );
            return;
        }

        /*
         * 추가 체결이 없어도 취소로 인해 remainingQuantity 등이
         * 변경될 수 있으므로 최신 누적 결과를 항상 반영한다.
         */
        execution.update(
                totalFilledQuantity,
                averageExecutionPrice,
                totalExecutionAmount,
                remainingQuantity
        );
    }

    @Transactional
    public void markExecutionChecked(
            UUID orderId,
            Instant checkedAt
    ) {
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        order.markExecutionChecked(checkedAt);
    }
}