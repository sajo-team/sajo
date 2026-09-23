package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.cache.SignalStateStore;
import com.sajo.market_service.strategy.client.user.AccountHoldingFeignClient;
import com.sajo.market_service.strategy.client.user.dto.AccountHoldingPositionResponse;
import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.kafka.dto.SignalType;
import com.sajo.market_service.strategy.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.market_service.strategy.kafka.dto.TradingSignalPayload;
import com.sajo.market_service.strategy.kafka.producer.TradingSignalProducer;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final MeterRegistry meterRegistry;
    private final AccountHoldingFeignClient accountHoldingFeignClient;

    private static final String EVALUATION_EVENT_KEY_PREFIX = "strategy:evaluation:event:";
    private static final String SIGNAL_STATE_KEY_PREFIX = "strategy:evaluation:state:";
    private static final String ENTRY_PRICE_KEY_PREFIX = "strategy:evaluation:entry-price:";
    private static final String SIGNAL_PUBLISHED_METRIC = "strategy_signal_published_total";
    private static final String SIGNAL_DUPLICATE_BLOCKED_METRIC = "strategy_signal_duplicate_blocked_total";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofDays(1);
    private static final Duration SIGNAL_STATE_TTL = Duration.ofDays(7);

//    진입가(근사치) 캐시 TTL. SIGNAL_STATE_TTL(7일)보다 길게 잡아, 스윙 보유 기간 동안 만료로
//    손절/목표수익 조건이 갑자기 절대가 조건으로만 폴백되는 상황을 줄인다. 만료돼도 절대가 조건은
//    계속 유효하므로 안전하게 저하될 뿐이다.
    private static final Duration ENTRY_PRICE_TTL = Duration.ofDays(30);

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
        String entryPriceKey = createEntryPriceKey(strategy.getId());
        Long entryPrice = readEntryPrice(entryPriceKey);

        SignalType signalType = resolveSignalType(strategy, request.currentPrice(), entryPrice);
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
            meterRegistry.counter(SIGNAL_DUPLICATE_BLOCKED_METRIC).increment();
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
                createSignalReason(signalType, strategy, request.currentPrice(), entryPrice)
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

        // 발행이 확정된 시점(= 실제 거래가 일어날 Signal)에 진입가 캐시를 갱신한다.
        // BUY는 진입가를 저장하고(가능하면 실제 평균 매입가, 실패 시 발행가 근사치), SELL은 포지션
        // 종료로 보고 다음 BUY 사이클을 위해 지운다. complete() 성공 여부와 무관하게 수행한다.
        if (signalType == SignalType.BUY) {
            updateEntryPriceOnBuyConfirmed(strategy, entryPriceKey, request.currentPrice());
        } else {
            stringRedisTemplate.delete(entryPriceKey);
        }

        boolean completed = signalStateStore.complete(signalStateKey, claimToken, signalType.name(), SIGNAL_STATE_TTL);
        if (!completed) {
            // Signal은 이미 Kafka로 발행됐지만 로컬 상태 반영에는 실패한 상황(PROCESSING TTL 만료 후 다른 요청이 재선점한 경우 등)이라
            // 중복 발행 가능성이 남는다. Redis-Kafka 간 원자성은 별도 문제
            // TODO: Outbox/멱등 Producer-Consumer 도입이 필요
            log.warn("Signal 완료 처리에 실패했습니다(다른 요청이 상태를 재선점했을 수 있음). strategyId={}", strategy.getId());
        }

        meterRegistry.counter(SIGNAL_PUBLISHED_METRIC, "signalType", signalType.name()).increment();

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

    private String createEntryPriceKey(UUID strategyId) {
        return ENTRY_PRICE_KEY_PREFIX + strategyId;
    }

    private Long readEntryPrice(String entryPriceKey) {
        String value = stringRedisTemplate.opsForValue().get(entryPriceKey);
        return value != null ? Long.valueOf(value) : null;
    }

    /**
     * BUY Signal 확정 시점에 Account 서비스(user-service)에서 실제 평균 매입가를 1회 조회해 진입가로
     * 저장한다. trading-service가 이 Signal을 소비해 KIS에 실제 주문을 내는 과정은 비동기라, 이 시점에는
     * 아직 계좌에 반영되지 않아 조회에 실패하는 경우가 흔할 수 있다 — 그 경우 발행가 근사치로 폴백해
     * SELL 조건 판단 자체가 막히지 않게 한다.
     */
    private void updateEntryPriceOnBuyConfirmed(Strategy strategy, String entryPriceKey, Long fallbackPrice) {
        Long entryPrice = fallbackPrice;

        try {
            AccountHoldingPositionResponse position =
                    accountHoldingFeignClient.getHoldingPosition(strategy.getUserId(), strategy.getStockCode());

            if (position.quantity() != null && position.quantity() > 0 && position.avgPurchasePrice() != null) {
                entryPrice = position.avgPurchasePrice().longValue();
            } else {
                log.info("Account 서비스 보유 포지션이 아직 반영되지 않아 발행가 근사치로 진입가를 저장합니다. strategyId={}",
                        strategy.getId());
            }
        } catch (RuntimeException e) {
            log.info("Account 서비스에서 평균 매입가 조회에 실패해 발행가 근사치로 진입가를 저장합니다. strategyId={}, error={}",
                    strategy.getId(), e.getMessage());
        }

        stringRedisTemplate.opsForValue().set(entryPriceKey, String.valueOf(entryPrice), ENTRY_PRICE_TTL);
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
            Long currentPrice,
            Long entryPrice
    ) {
        return switch(signalType) {
            case BUY -> String.format(
                    "현재가(%d)가 매수 조건 가격 (%d) 이하입니다.",
                    currentPrice,
                    strategy.getBuyConditionPrice()
            );

            case SELL -> createSellSignalReason(strategy, currentPrice, entryPrice);
        };
    }

    private String createSellSignalReason(
            Strategy strategy,
            Long currentPrice,
            Long entryPrice
    ) {
        if (isStopLossTriggered(strategy, currentPrice, entryPrice)) {
            return String.format(
                    "진입가(%d) 대비 현재가(%d) 하락률이 손절률(%s%%) 이상입니다.",
                    entryPrice, currentPrice, strategy.getStopLossRate()
            );
        }

        if (isTargetReturnTriggered(strategy, currentPrice, entryPrice)) {
            return String.format(
                    "진입가(%d) 대비 현재가(%d) 상승률이 목표수익률(%s%%) 이상입니다.",
                    entryPrice, currentPrice, strategy.getTargetReturnRate()
            );
        }

        return String.format(
                "현재가(%d)가 매도 조건 가격(%d) 이상입니다.",
                currentPrice,
                strategy.getSellConditionPrice()
        );
    }

    private SignalType resolveSignalType(
            Strategy strategy,
            Long currentPrice,
            Long entryPrice
    ) {
        boolean buyMatched = currentPrice <= strategy.getBuyConditionPrice();

        boolean sellMatched = currentPrice >= strategy.getSellConditionPrice()
                || isStopLossTriggered(strategy, currentPrice, entryPrice)
                || isTargetReturnTriggered(strategy, currentPrice, entryPrice);

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

    /**
     * 진입가(근사치) 대비 현재가 하락률이 손절률 이상인지 확인한다. 진입가가 없으면(BUY 이력 없음) 항상 false.
     */
    private boolean isStopLossTriggered(Strategy strategy, Long currentPrice, Long entryPrice) {
        if (entryPrice == null) {
            return false;
        }
        BigDecimal lossRate = changeRate(entryPrice, currentPrice).negate();
        return lossRate.compareTo(strategy.getStopLossRate()) >= 0;
    }

    /**
     * 진입가(근사치) 대비 현재가 상승률이 목표수익률 이상인지 확인한다. 진입가가 없거나
     * targetReturnRate가 설정되지 않은 전략(선택값)이면 항상 false.
     */
    private boolean isTargetReturnTriggered(Strategy strategy, Long currentPrice, Long entryPrice) {
        if (entryPrice == null || strategy.getTargetReturnRate() == null) {
            return false;
        }
        return changeRate(entryPrice, currentPrice).compareTo(strategy.getTargetReturnRate()) >= 0;
    }

    private BigDecimal changeRate(Long entryPrice, Long currentPrice) {
        return BigDecimal.valueOf(currentPrice - entryPrice)
                .divide(BigDecimal.valueOf(entryPrice), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private void validateOrderAmount(Strategy strategy) {
        Long orderAmount = strategy.getOrderAmount();


        if (orderAmount == null || orderAmount <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "1회 주문 금액이 없어 Signal을 생성할 수 없습니다.");
        }
    }
}
