package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalPayload;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingSignalCommandService {
    private final OrderCommandRepository orderCommandRepository;
    private final AutoTradingCommandRepository autoTradingCommandRepository;
    private final TradingLimitCommandRepository tradingLimitCommandRepository;


    @Transactional
    public void processSignal(TradingSignalGeneratedEvent event){

        TradingSignalPayload payload = event.payload();

        if(orderCommandRepository.existsBySignalId(payload.signalId())){
            log.info("이미 처리된 Signal입니다. signalId={}", payload.signalId());
            return;
        }

        AutoTrading autoTrading =
                autoTradingCommandRepository
                        .findByUserIdAndStrategyIdAndDeletedAtIsNull(
                                payload.userId(),
                                payload.strategyId()
                        )
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                                )
                        );
        if(!autoTrading.getEnabled()){
            throw new BusinessException(
                    TradingErrorCode.AUTO_TRADING_DISABLED
            );
        }

        TradingLimit tradingLimit =
                tradingLimitCommandRepository.findByUserIdForUpdate(
                                payload.userId()
                        )
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.TRADING_LIMIT_NOT_FOUND
                                )
                        );
        int orderQuantity = calculateOrderQuantity(payload);

        validateDailyTradingLimit(
                payload,
                tradingLimit,
                orderQuantity
        );

        Order order = Order.create(
                payload.userId(),
                autoTrading.getId(),
                payload.strategyId(),
                payload.signalId(),
                payload.stockCode(),
                payload.signalType(),
                payload.triggerPrice(),
                orderQuantity
        );

        orderCommandRepository.save(order);

    }


    private int calculateOrderQuantity(TradingSignalPayload payload){ // 주문 수량 계산
        long calculatedQuantity =
                payload.orderAmount() / payload.triggerPrice();

        int orderQuantity = Math.toIntExact(calculatedQuantity);

        if(orderQuantity <= 0){
            throw new BusinessException(
                    TradingErrorCode.ORDER_QUANTITY_NOT_AVAILABLE
            );
        }
        return orderQuantity;
    }


    private void validateDailyTradingLimit( // 검증(하루 최대 주문 횟수, 금액)
            TradingSignalPayload payload,
            TradingLimit tradingLimit,
            int orderQuantity
    ){
        ZoneId zoneId = ZoneId.of("Asia/Seoul");

        Instant startOfDay = LocalDate.now(zoneId)
                .atStartOfDay(zoneId)
                .toInstant();

        Instant startOfNextDay = LocalDate.now(zoneId)
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant();

        long todayOrderCount =
                orderCommandRepository.countOrdersByUserIdAndCreatedAtBetween(
                        payload.userId(),
                        OrderStatus.FAILED,
                        startOfDay,
                        startOfNextDay
                );

        long todayOrderAmount =
                orderCommandRepository.sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
                        payload.userId(),
                        OrderStatus.FAILED,
                        startOfDay,
                        startOfNextDay
                );

        long currentOrderAmount =
                payload.triggerPrice() * orderQuantity;


        if(todayOrderCount + 1 > tradingLimit.getDailyMaxOrderCount()){
            throw new BusinessException(
                    TradingErrorCode.DAILY_ORDER_COUNT_LIMIT_EXCEEDED
            );
        }

        if(todayOrderAmount + currentOrderAmount > tradingLimit.getDailyMaxOrderAmount()){
            throw new BusinessException(
                    TradingErrorCode.DAILY_ORDER_AMOUNT_LIMIT_EXCEEDED
            );
        }
    }
}
