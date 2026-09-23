package com.sajo.market_service.strategy.service.command;

import com.sajo.market_service.strategy.cache.SignalStateStore;
import com.sajo.market_service.strategy.client.user.AccountHoldingFeignClient;
import com.sajo.market_service.strategy.client.user.dto.AccountHoldingPositionResponse;
import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.kafka.dto.SignalType;
import com.sajo.market_service.strategy.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.market_service.strategy.kafka.producer.TradingSignalProducer;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
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

    @Mock
    private AccountHoldingFeignClient accountHoldingFeignClient;

    private static final Duration ENTRY_PRICE_TTL = Duration.ofDays(30);

    private StrategyEvaluationService strategyEvaluationService;
    private SimpleMeterRegistry meterRegistry;
    private Strategy strategy;
    private String signalStateKey;
    private String entryPriceKey;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        strategyEvaluationService = new StrategyEvaluationService(
                strategyQueryRepository, tradingSignalProducer, stringRedisTemplate, signalStateStore, meterRegistry,
                accountHoldingFeignClient
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
        entryPriceKey = "strategy:evaluation:entry-price:" + strategy.getId();

        lenient().when(strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .thenReturn(List.of(strategy));
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 이벤트 중복 처리 방지 락은 항상 최초 획득에 성공한 것으로 가정한다(이 테스트의 관심사가 아님).
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        // 진입가는 기본적으로 없는 상태(BUY 이력 없음)로 가정한다. 필요한 테스트에서만 오버라이드한다.
        lenient().when(valueOperations.get(entryPriceKey)).thenReturn(null);
        // BUY 확정 직후엔 trading-service의 비동기 처리 특성상 계좌에 아직 반영되지 않은 경우가
        // 흔하므로, 기본값은 조회 실패(발행가 근사치로 폴백)로 가정한다. 실제 조회 성공 케이스는
        // 별도 테스트에서 오버라이드한다.
        lenient().doThrow(new RuntimeException("포지션 미반영(테스트 기본값)"))
                .when(accountHoldingFeignClient).getHoldingPosition(any(), anyString());
        lenient().when(signalStateStore.complete(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_STATE_TTL)))
                .thenReturn(true);
        lenient().when(signalStateStore.complete(eq(signalStateKey), anyString(), eq("SELL"), eq(SIGNAL_STATE_TTL)))
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
        assertThatCounter("strategy_signal_published_total", "signalType", "BUY").isEqualTo(1.0);
        assertThatCounter("strategy_signal_duplicate_blocked_total").isEqualTo(1.0);
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
        assertThatCounter("strategy_signal_published_total", "signalType", "BUY").isEqualTo(2.0);
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

    @Test
    @DisplayName("BUY Signal 발행이 확정됐지만 Account 서비스 조회에 실패하면 발행가를 진입가 근사치로 저장한다")
    void savesEntryPriceApproximationWhenBuySignalPublished() {
        org.mockito.BDDMockito.given(signalStateStore.claim(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true);

        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 65_000L));

        verify(valueOperations).set(entryPriceKey, "65000", ENTRY_PRICE_TTL);
    }

    @Test
    @DisplayName("BUY Signal 확정 시 Account 서비스 조회에 성공하면 실제 평균 매입가를 진입가로 저장한다")
    void savesActualAvgPurchasePriceWhenAccountServiceRespondsSuccessfully() {
        org.mockito.BDDMockito.given(signalStateStore.claim(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true);
        doReturn(new AccountHoldingPositionResponse(10L, new BigDecimal("64850"), new BigDecimal("0.23")))
                .when(accountHoldingFeignClient).getHoldingPosition(strategy.getUserId(), STOCK_CODE);

        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 65_000L));

        verify(valueOperations).set(entryPriceKey, "64850", ENTRY_PRICE_TTL);
    }

    @Test
    @DisplayName("BUY Signal 확정 시 Account 서비스가 미보유(수량 0)를 응답하면 발행가로 폴백한다")
    void fallsBackToApproximationWhenAccountServiceReportsZeroQuantity() {
        org.mockito.BDDMockito.given(signalStateStore.claim(eq(signalStateKey), anyString(), eq("BUY"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true);
        doReturn(new AccountHoldingPositionResponse(0L, BigDecimal.ZERO, BigDecimal.ZERO))
                .when(accountHoldingFeignClient).getHoldingPosition(strategy.getUserId(), STOCK_CODE);

        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 65_000L));

        verify(valueOperations).set(entryPriceKey, "65000", ENTRY_PRICE_TTL);
    }

    @Test
    @DisplayName("SELL Signal 발행이 확정되면 진입가 키를 삭제해 포지션 종료를 반영한다")
    void deletesEntryPriceWhenSellSignalPublished() {
        org.mockito.BDDMockito.given(signalStateStore.claim(eq(signalStateKey), anyString(), eq("SELL"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true);

        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 85_000L));

        verify(stringRedisTemplate).delete(entryPriceKey);
    }

    @Test
    @DisplayName("절대 매도 조건가에 도달하지 않아도 진입가 대비 손절률을 넘으면 SELL Signal을 발행한다")
    void publishesSellSignalWhenStopLossRateExceeded() {
        // 매수/매도 절대가 구간을 넓게 잡아 손절률 조건과 겹치지 않게 한다.
        Strategy stopLossStrategy = Strategy.create(
                UUID.randomUUID(), UUID.randomUUID(), STOCK_CODE, "손절 테스트 전략",
                50_000L, 90_000L, new BigDecimal("5.0000"), null,
                3_000_000L, 100_000L, null, null, null
        );
        String stateKey = "strategy:evaluation:state:" + stopLossStrategy.getId();
        String priceKey = "strategy:evaluation:entry-price:" + stopLossStrategy.getId();

        given(strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .willReturn(List.of(stopLossStrategy));
        given(valueOperations.get(priceKey)).willReturn("70000");
        given(signalStateStore.claim(eq(stateKey), anyString(), eq("SELL"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true);

        // 진입가(70000) 대비 (70000-66000)/70000 = 5.71% 하락 → 손절률(5%) 이상
        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 66_000L));

        ArgumentCaptor<TradingSignalGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(TradingSignalGeneratedEvent.class);
        verify(tradingSignalProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().signalType()).isEqualTo(SignalType.SELL);
    }

    @Test
    @DisplayName("절대 매도 조건가에 도달하지 않아도 진입가 대비 목표수익률을 넘으면 SELL Signal을 발행한다")
    void publishesSellSignalWhenTargetReturnRateExceeded() {
        Strategy targetReturnStrategy = Strategy.create(
                UUID.randomUUID(), UUID.randomUUID(), STOCK_CODE, "목표수익 테스트 전략",
                50_000L, 90_000L, new BigDecimal("5.0000"), new BigDecimal("3.0000"),
                3_000_000L, 100_000L, null, null, null
        );
        String stateKey = "strategy:evaluation:state:" + targetReturnStrategy.getId();
        String priceKey = "strategy:evaluation:entry-price:" + targetReturnStrategy.getId();

        given(strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .willReturn(List.of(targetReturnStrategy));
        given(valueOperations.get(priceKey)).willReturn("70000");
        given(signalStateStore.claim(eq(stateKey), anyString(), eq("SELL"), eq(SIGNAL_CLAIM_TTL)))
                .willReturn(true);

        // 진입가(70000) 대비 (73000-70000)/70000 = 4.28% 상승 → 목표수익률(3%) 이상
        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 73_000L));

        ArgumentCaptor<TradingSignalGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(TradingSignalGeneratedEvent.class);
        verify(tradingSignalProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().signalType()).isEqualTo(SignalType.SELL);
    }

    @Test
    @DisplayName("진입가가 없으면(BUY 이력 없음) 손절/목표수익 조건은 무시되고 절대가 조건만 적용된다")
    void ignoresStopLossAndTargetReturnWhenNoEntryPrice() {
        // 절대 매수/매도 조건 사이 구간(70000~80000)이라 절대가 조건은 미충족.
        // entryPrice는 setUp()에서 기본 null이므로 손절/목표수익 조건도 항상 false여야 한다.
        strategyEvaluationService.evaluate(evaluationRequest(UUID.randomUUID(), 75_000L));

        verify(tradingSignalProducer, never()).publish(any());
        verify(signalStateStore).clearIfNotProcessing(signalStateKey);
    }

    private StrategyEvaluationRequest evaluationRequest(UUID sourceEventId, Long currentPrice) {
        return new StrategyEvaluationRequest(sourceEventId, STOCK_CODE, currentPrice, Instant.now());
    }

    private org.assertj.core.api.AbstractDoubleAssert<?> assertThatCounter(String name, String... tagKeyValues) {
        io.micrometer.core.instrument.search.RequiredSearch search = meterRegistry.get(name);
        for (int i = 0; i < tagKeyValues.length; i += 2) {
            search = search.tag(tagKeyValues[i], tagKeyValues[i + 1]);
        }
        return org.assertj.core.api.Assertions.assertThat(search.counter().count());
    }
}
