package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.OrderManualResolutionRequest;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderAdminCommandService {

    private final OrderCommandRepository orderCommandRepository;
    private final OrderExecutionCommandService orderExecutionCommandService;

    @Transactional
    public void resolveTimeoutOrder(
            UUID orderId,
            OrderManualResolutionRequest request
    ) {
        Order order = orderCommandRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() ->
                        new BusinessException(
                                TradingErrorCode.ORDER_NOT_FOUND
                        )
                );

        order.validateManualResolutionAllowed();

        switch (request.resolution()) {

            case FAILED -> order.fail(
                    "MANUAL_RESOLUTION_FAILED",
                    request.reason()
            );

            case ACCEPTED -> order.accept(
                    request.brokerOrderNo()
            );

            case CANCELED -> resolveCanceled(
                    order,
                    request
            );
        }
    }

    private void resolveCanceled(
            Order order,
            OrderManualResolutionRequest request
    ) {
        if (request.brokerOrderNo() == null
                || request.brokerOrderNo().isBlank()
                || request.totalFilledQuantity() == null) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        int totalFilledQuantity =
                request.totalFilledQuantity();

        if (totalFilledQuantity == 0) {
            orderExecutionCommandService.applyReconciledCancellation(
                    order,
                    request.brokerOrderNo(),
                    0,
                    BigDecimal.ZERO,
                    0L
            );
            return;
        }

        if (request.averageExecutionPrice() == null
                || request.totalExecutionAmount() == null) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        orderExecutionCommandService.applyReconciledCancellation(
                order,
                request.brokerOrderNo(),
                totalFilledQuantity,
                request.averageExecutionPrice(),
                request.totalExecutionAmount()
        );
    }
}