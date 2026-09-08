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
            long averageExecutionPrice,
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
            long averageExecutionPrice,
            long totalExecutionAmount
    ){
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        int newlyFilledQuantity =
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
         * 이전 조회 이후 추가 체결이 없더라도
         * 취소 시점의 최신 누적 체결 결과를 반영할 수 있다.
         */
        if (newlyFilledQuantity == 0) {
            return;
        }

        execution.update(
                totalFilledQuantity,
                averageExecutionPrice,
                totalExecutionAmount,
                remainingQuantity
        );
    }
}