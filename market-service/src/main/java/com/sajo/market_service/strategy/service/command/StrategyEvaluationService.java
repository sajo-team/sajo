package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.kafka.dto.SignalType;
import com.sajo.market_service.strategy.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.market_service.strategy.kafka.dto.TradingSignalPayload;
import com.sajo.market_service.strategy.kafka.producer.TradingSignalProducer;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyEvaluationService {

    private final StrategyQueryRepository strategyQueryRepository;
    private final TradingSignalProducer tradingSignalProducer;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PROCESSED_EVENT_KEY_PREFIX = "strategy:evaluation:processed:";
    private static final Duration PROCESSED_EVENT_TTL = Duration.ofDays(1);

    public void evaluate(StrategyEvaluationRequest request) {
        String eventKey = PROCESSED_EVENT_KEY_PREFIX + request.sourceEventId();
        boolean firstProcessing = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(
                        eventKey,
                        "1",
                        PROCESSED_EVENT_TTL
                )
        );

        if (!firstProcessing) {
            log.info("이미 처리된 전략 평가 이벤트입니다. sourceEventId={}", request.sourceEventId());
            return;
        }

        List<Strategy> strategies =
                strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(
                        request.stockCode(),
                        StrategyStatus.ACTIVE
                );

        for (Strategy strategy : strategies) {
            try {
                evaluateStrategy(strategy, request);
            } catch (BusinessException e) {
                log.warn("전략 평가실패. strategyId={}, errorCode={}",
                        strategy.getId(),
                        e.getErrorCode()
                );
            } catch (Exception e) {
                log.error("전략 평가 중 예외가 발생했습니다. strategyId={}", strategy.getId(), e);
            }
        }
    }

    private void evaluateStrategy(
            Strategy strategy,
            StrategyEvaluationRequest request
    ) {
        SignalType signalType = resolveSignalType(strategy, request.currentPrice());

        if (signalType == null) {
            log.debug("전략 조건 미충족. strategyId={}, stockCode={}, currentPrice={}",
                    strategy.getId(),
                    strategy.getStockCode(),
                    request.currentPrice()
            );
            return;
        }

        validateOrderAmount(strategy);

        TradingSignalPayload payload = new TradingSignalPayload(
                UUID.randomUUID(),
                strategy.getId(),
                strategy.getUserId(),
                strategy.getStockCode(),
                signalType,
                request.currentPrice(),
                strategy.getOrderAmount(),
                createSignalReason(signalType, strategy, request.currentPrice())
        );

        TradingSignalGeneratedEvent event = new TradingSignalGeneratedEvent(
                UUID.randomUUID(),
                "TRADING_SIGNAL_GENERATED",
                1,
                request.baseTime(),
                strategy.getUserId(),
                payload
        );

        tradingSignalProducer.publish(event);

        log.info(
                "Trading Signal 발행 완료. signalId={}, strategyId={}, signalType={}",
                payload.signalId(),
                payload.strategyId(),
                payload.signalType()
        );
    }

    private String createSignalReason(
            SignalType signalType,
            Strategy strategy,
            Long currentPrice
    ) {
        return switch(signalType) {
            case BUY -> String.format(
                    "현재가(%d)가 매수 조건 가격 (%d) 이하입니다.",
                    currentPrice,
                    strategy.getBuyConditionPrice()
            );

            case SELL -> String.format(
                    "현재가(%d)가 매도 조건 가격(%d) 이상입니다.",
                    currentPrice,
                    strategy.getSellConditionPrice()
            );
        };
    }

    private SignalType resolveSignalType(
            Strategy strategy,
            Long currentPrice
    ) {
        boolean buyMatched = currentPrice <= strategy.getBuyConditionPrice();

        boolean sellMatched = currentPrice >= strategy.getSellConditionPrice();

        if (buyMatched && sellMatched) {
            log.warn("매수/매도 조건이 동시에 만족되어 Signal을 발행하지 않습니다. strategyId={}", strategy.getId());
            return null;
        }

        if (buyMatched) {
            return SignalType.BUY;
        }

        if (sellMatched) {
            return SignalType.SELL;
        }

        return null;
    }

    private void validateOrderAmount(Strategy strategy) {
        Long orderAmount = strategy.getOrderAmount();


        if (orderAmount == null || orderAmount <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "1회 주문 금액이 없어 Signal을 생성할 수 없습니다.");
        }
    }
}
