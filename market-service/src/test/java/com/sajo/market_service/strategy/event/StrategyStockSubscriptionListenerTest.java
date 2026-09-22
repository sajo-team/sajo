package com.sajo.market_service.strategy.event;

import com.sajo.market_service.market.config.MarketWebSocketProperties;
import com.sajo.market_service.market.websocket.KisWebSocketClient;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StrategyStockSubscriptionListenerTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private KisWebSocketClient kisWebSocketClient;

    @Mock
    private StrategyEvaluationService strategyEvaluationService;

    @Mock
    private StrategyQueryRepository strategyQueryRepository;

    @Test
    @DisplayName("전략 활성화 이벤트를 받으면 WebSocket 구독을 요청한다")
    void subscribesOnActivation() {
        StrategyStockSubscriptionListener listener = listenerWithTargetStockCodes(List.of());
        UUID strategyId = UUID.randomUUID();

        listener.handle(new StrategyActivationChangedEvent(strategyId, STOCK_CODE, true));

        verify(kisWebSocketClient).subscribe(STOCK_CODE);
        verify(strategyEvaluationService, never()).clearSignalState(strategyId);
    }

    @Test
    @DisplayName("비활성화 시 같은 종목을 쓰는 다른 활성 전략이 없고 정적 구독 대상도 아니면 구독을 해제한다")
    void unsubscribesWhenNoOtherActiveStrategyAndNotStaticTarget() {
        StrategyStockSubscriptionListener listener = listenerWithTargetStockCodes(List.of());
        UUID strategyId = UUID.randomUUID();
        given(strategyQueryRepository.existsByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .willReturn(false);

        listener.handle(new StrategyActivationChangedEvent(strategyId, STOCK_CODE, false));

        verify(strategyEvaluationService).clearSignalState(strategyId);
        verify(kisWebSocketClient).unsubscribe(STOCK_CODE);
    }

    @Test
    @DisplayName("비활성화 시 같은 종목을 쓰는 다른 활성 전략이 남아있으면 구독을 해제하지 않는다")
    void doesNotUnsubscribeWhenAnotherActiveStrategyExists() {
        StrategyStockSubscriptionListener listener = listenerWithTargetStockCodes(List.of());
        UUID strategyId = UUID.randomUUID();
        given(strategyQueryRepository.existsByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .willReturn(true);

        listener.handle(new StrategyActivationChangedEvent(strategyId, STOCK_CODE, false));

        verify(strategyEvaluationService).clearSignalState(strategyId);
        verify(kisWebSocketClient, never()).unsubscribe(STOCK_CODE);
    }

    @Test
    @DisplayName("비활성화 시 정적으로 설정된 구독 대상 종목이면 다른 활성 전략이 없어도 구독을 해제하지 않는다")
    void doesNotUnsubscribeStaticTargetStockCode() {
        StrategyStockSubscriptionListener listener = listenerWithTargetStockCodes(List.of(STOCK_CODE));
        UUID strategyId = UUID.randomUUID();
        given(strategyQueryRepository.existsByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .willReturn(false);

        listener.handle(new StrategyActivationChangedEvent(strategyId, STOCK_CODE, false));

        verify(strategyEvaluationService).clearSignalState(strategyId);
        verify(kisWebSocketClient, never()).unsubscribe(STOCK_CODE);
    }

    private StrategyStockSubscriptionListener listenerWithTargetStockCodes(List<String> targetStockCodes) {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                false, null, null, null, null, 2.0, targetStockCodes, null, 0
        );
        return new StrategyStockSubscriptionListener(
                kisWebSocketClient, strategyEvaluationService, strategyQueryRepository, properties
        );
    }
}
