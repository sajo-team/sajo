package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.cache.SignalStateStore;
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

import java.nio.charset.StandardCharsets;
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
    private final SignalStateStore signalStateStore;

    private static final String EVALUATION_EVENT_KEY_PREFIX = "strategy:evaluation:event:";
    private static final String SIGNAL_STATE_KEY_PREFIX = "strategy:evaluation:state:";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofDays(1);
    private static final Duration SIGNAL_STATE_TTL = Duration.ofDays(7);

//    선점(PROCESSING) 상태의 TTL. {@link TradingSignalProducer}가 최대 10초까지 블로킹하므로
//    정상 처리 시간을 넉넉히 덮으면서도, 애플리케이션이 완료/해제 없이 죽었을 때 상태가 영구히 잠기지 않도록 짧게 잡는다.
    private static final Duration SIGNAL_CLAIM_TTL = Duration.ofSeconds(30);

    public void evaluate(StrategyEvaluationRequest request) {
        List<Strategy> strategies =
                strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(
                        request.stockCode(),
                        StrategyStatus.ACTIVE
        );

        for (Strategy strategy : strategies) {
            String eventKey = createEventKey(request.sourceEventId(), strategy.getId());
            boolean acquired = false;

            try {
                acquired = Boolean.TRUE.equals(
                        stringRedisTemplate.opsForValue().setIfAbsent(
                                eventKey,
                                "PROCESSING",
                                PROCESSING_TTL
                        )
                );

                if (!acquired) {
                    log.info(
                            "이미 처리 중이거나 처리 완료된 전략 평가 이벤트입니다. sourceEventId={}, strategyId={}",
                            request.sourceEventId(),
                            strategy.getId()
                    );
                    continue;
                }

                evaluateStrategy(strategy, request);
                stringRedisTemplate.opsForValue().set(eventKey, "COMPLETED", COMPLETED_TTL);
            } catch (BusinessException e) {
                deleteEvaluationEventKey(eventKey, acquired);
                log.warn("전략 평가실패. strategyId={}, errorCode={}",
                        strategy.getId(),
                        e.getErrorCode()
                );
            } catch (Exception e) {
                deleteEvaluationEventKey(eventKey, acquired);
                log.error("전략 평가 중 예외가 발생했습니다. strategyId={}", strategy.getId(), e);
            }
        }
    }

    private String createEventKey(UUID sourceEventId, UUID strategyId) {
        return EVALUATION_EVENT_KEY_PREFIX + sourceEventId + ":" + strategyId;
    }

    private void deleteEvaluationEventKey(String eventKey, boolean acquired) {
        if (!acquired) {
            return;
        }

        try {
            stringRedisTemplate.delete(eventKey);
        } catch (Exception exception) {
            log.error("전략 평가 이벤트 키 삭제에 실패했습니다. eventKey={}", eventKey, exception);
        }
    }

    /**
     * 전략 비활성화 등으로 조건 구간 상태를 초기화해, 다음 평가부터 다시 Signal을 발행할 수 있게 한다.
     * 다른 요청이 한창 선점 중(PROCESSING:*)이면 건드리지 않는다 — 비활성화와 Signal 발행이 동시에
     * 일어나도 그 요청의 선점 상태를 실수로 지우지 않기 위함이다.
     */
    public void clearSignalState(UUID strategyId) {
        signalStateStore.clearIfNotProcessing(createSignalStateKey(strategyId));
    }

    private void evaluateStrategy(
            Strategy strategy,
            StrategyEvaluationRequest request
    ) {
        SignalType signalType = resolveSignalType(strategy, request.currentPrice());
        String signalStateKey = createSignalStateKey(strategy.getId());

        if (signalType == null) {
            log.debug("전략 조건 미충족. strategyId={}, stockCode={}, currentPrice={}",
                    strategy.getId(),
                    strategy.getStockCode(),
                    request.currentPrice()
            );
            signalStateStore.clearIfNotProcessing(signalStateKey);
            return;
        }

        validateOrderAmount(strategy);

        String claimToken = UUID.randomUUID().toString();
        if (!signalStateStore.claim(signalStateKey, claimToken, signalType.name(), SIGNAL_CLAIM_TTL)) {
            log.debug("동일 조건 구간에서 이미 Signal을 발행했거나 다른 평가가 처리 중이라 건너뜁니다. strategyId={}, signalType={}",
                    strategy.getId(), signalType);
            return;
        }

        TradingSignalPayload payload = new TradingSignalPayload(
                createDeterministicSignalId(
                        request.sourceEventId(),
                        strategy.getId(),
                        signalType
                ),
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

        try {
            tradingSignalProducer.publish(event);
        } catch (RuntimeException e) {
            // 선점(claim) 이후 실제 발행에 실패하면 "내 토큰"일 때만 상태를 되돌려, 그 사이 다른 요청이
            // 새로 선점/완료한 상태를 실수로 지우지 않으면서 다음 평가에서 재시도할 수 있게 한다.
            signalStateStore.release(signalStateKey, claimToken);
            throw e;
        }

        boolean completed = signalStateStore.complete(signalStateKey, claimToken, signalType.name(), SIGNAL_STATE_TTL);
        if (!completed) {
            // Signal은 이미 Kafka로 발행됐지만 로컬 상태 반영에는 실패한 상황(PROCESSING TTL 만료 후 다른 요청이 재선점한 경우 등)이라
            // 중복 발행 가능성이 남는다. Redis-Kafka 간 원자성은 별도 문제
            // TODO: Outbox/멱등 Producer-Consumer 도입이 필요
            log.warn("Signal 완료 처리에 실패했습니다(다른 요청이 상태를 재선점했을 수 있음). strategyId={}", strategy.getId());
        }

        log.info(
                "Trading Signal 발행 완료. signalId={}, strategyId={}, signalType={}",
                payload.signalId(),
                payload.strategyId(),
                payload.signalType()
        );
    }

    private String createSignalStateKey(UUID strategyId) {
        return SIGNAL_STATE_KEY_PREFIX + strategyId;
    }

    private UUID createDeterministicSignalId(
            UUID sourceEventId,
            UUID strategyId,
            SignalType signalType
    ) {
        String identity = sourceEventId + ":" + strategyId + ":" + signalType;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
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
