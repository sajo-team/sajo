package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderStatusCommandService {
    private final OrderCommandRepository orderCommandRepository;
    private static final int MAX_ACCOUNT_RETRY_COUNT = 3;

    @Transactional
    public void accept(UUID orderId, String brokerOrderNo){
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );
        order.accept(brokerOrderNo);
    }

    @Transactional
    public void fail(UUID orderId, String failureCode, String failureMessage){
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        order.fail(failureCode, failureMessage);
    }

    @Transactional
    public void timeout(UUID orderId, String failureCode, String failureMessage){
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );
        order.timeout(failureCode, failureMessage);
    }

    @Transactional
    public Order startProcessing(UUID orderId){
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        order.startProcessing();

        return order;
    }

    @Transactional
    public void retry(UUID orderId) {
        Order order =
                orderCommandRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        order.retry(
                MAX_ACCOUNT_RETRY_COUNT,
                "ACCOUNT_RETRY_EXHAUSTED",
                "계좌 정보 조회 재시도 횟수를 초과했습니다."
        );
    }
}
