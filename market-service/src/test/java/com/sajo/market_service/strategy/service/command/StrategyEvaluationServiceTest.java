package com.sajo.market_service.strategy.service.command;

import com.sajo.market_service.strategy.cache.SignalStateStore;
import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.market_service.strategy.kafka.producer.TradingSignalProducer;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StrategyEvaluationServiceTest {

    private static final String STOCK_CODE = "005930";
    private static final Duration SIGNAL_CLAIM_TTL = Duration.ofSeconds(30);
    private static final Duration SIGNAL_STATE_TTL = Duration.ofDays(7);

    @Mock
    private StrategyQueryRepository strategyQueryRepository;

    @Mock
    private TradingSignalProducer tradingSignalProducer;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SignalStateStore signalStateStore;

    private StrategyEvaluationService strategyEvaluationService;
    private Strategy strategy;
    private String signalStateKey;

    @BeforeEach
    void setUp() {
        strategyEvaluationService = new StrategyEvaluationService(
                strategyQueryRepository, tradingSignalProducer, stringRedisTemplate, signalStateStore
        );
        strategy = Strategy.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                STOCK_CODE,
                "테스트 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                100_000L,
                null,
                null,
                null
        );
        signalStateKey = "strategy:evaluation:state:" + strategy.getId();

        lenient().when(strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .thenReturn(List.of(strategy));
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 이벤트 중복 처리 방지 락은 항상 최초 획득에 성공한 것으로 가정한다(이 테스트의 관심사가 아님).
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        lenient().when(signalStateStore.complete(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_STATE_TTL)))
                .thenReturn(true);
    }

    @Test
    @DisplayName("같은 조건 구간에서는 두 번째 평가부터 Signal을 발행하지 않는다(선점 실패 시 skip)")
    void skipsRepeatedSignalInSameConditionZone() {
        // 첫 평가: 선점 성공 → 발행, 두 번째 평가: 이미 같은 방향이라 선점 실패 → skip
        org.mockito.BDDMockito.given(signalStateStore.claim(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true, false);

        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 65_000L));
        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 60_000L));

        verify(tradingSignalProducer, times(1)).publish(any(TradingSignalGeneratedEvent.class));
    }

    @Test
    @DisplayName("조건 구간을 벗어났다가 다시 진입하면 Signal을 재발행한다")
    void republishesSignalAfterExitingAndReenteringConditionZone() {
        org.mockito.BDDMockito.given(signalStateStore.claim(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true, true);

        // 1) 매수 조건 진입 → 발행(선점 성공)
        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 65_000L));
        // 2) 두 조건 사이(조건 미충족) → 상태 초기화(선점 중이 아닐 때만 삭제)
        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 75_000L));
        // 3) 다시 매수 조건 진입 → 재발행(선점 성공)
        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 65_000L));

        verify(tradingSignalProducer, times(2)).publish(any(TradingSignalGeneratedEvent.class));
        verify(signalStateStore, times(1)).clearIfNotProcessing(signalStateKey);
    }

    @Test
    @DisplayName("선점 후 Signal 발행이 실패하면 내 토큰으로만 상태를 되돌린다")
    void releasesOwnClaimWhenPublishFails() {
        org.mockito.BDDMockito.given(signalStateStore.claim(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true);
        doThrow(new IllegalStateException("kafka down")).when(tradingSignalProducer).publish(any());

        // evaluate()는 전략 단위로 예외를 흡수하므로 publish 실패가 밖으로 전파되지 않는다
        org.assertj.core.api.Assertions.assertThatCode(() ->
                strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 65_000L))
        ).doesNotThrowAnyException();

        ArgumentCaptor<String> claimTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signalStateStore).claim(eq(signalStateKey), claimTokenCaptor.capture(), eq("BUY"), eq(SIGNAL_CLAIM_TTL));
        verify(signalStateStore).release(signalStateKey, claimTokenCaptor.getValue());
        verify(signalStateStore, never()).complete(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("clearSignalState는 선점 중이 아닐 때만 반복-방지 상태 키를 삭제한다")
    void clearSignalStateDelegatesToClearIfNotProcessing() {
        UUID strategyId = UUID.randomUUID();

        strategyEvaluationService.clearSignalState(strategyId);

        verify(signalStateStore).clearIfNotProcessing("strategy:evaluation:state:" + strategyId);
        verify(stringRedisTemplate, never()).delete(anyString());
        verify(tradingSignalProducer, never()).publish(any());
    }

    private StrategyEvaluationRequest evaluationRequest(UUID sourceEventId, Long currentPrice) {
        return new StrategyEvaluationRequest(sourceEventId, STOCK_CODE, currentPrice, Instant.now());
    }
}
