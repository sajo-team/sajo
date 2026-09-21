package com.sajo.market_service.strategy.event;

import com.sajo.market_service.market.websocket.KisWebSocketClient;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StrategyStockSubscriptionListenerTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private KisWebSocketClient kisWebSocketClient;

    @Mock
    private StrategyEvaluationService strategyEvaluationService;

    private StrategyStockSubscriptionListener listener;

    @Test
    @DisplayName("전략 활성화 이벤트를 받으면 WebSocket 구독을 요청한다")
    void subscribesOnActivation() {
        listener = new StrategyStockSubscriptionListener(kisWebSocketClient, strategyEvaluationService);
        UUID strategyId = UUID.randomUUID();

        listener.handle(new StrategyActivationChangedEvent(strategyId, STOCK_CODE, true));

        verify(kisWebSocketClient).subscribe(STOCK_CODE);
        verify(strategyEvaluationService, never()).clearSignalState(strategyId);
    }

    @Test
    @DisplayName("전략 비활성화 이벤트를 받으면 반복-방지 상태만 초기화하고 구독은 건드리지 않는다")
    void clearsSignalStateOnDeactivation() {
        listener = new StrategyStockSubscriptionListener(kisWebSocketClient, strategyEvaluationService);
        UUID strategyId = UUID.randomUUID();

        listener.handle(new StrategyActivationChangedEvent(strategyId, STOCK_CODE, false));

        verify(strategyEvaluationService).clearSignalState(strategyId);
        verify(kisWebSocketClient, never()).subscribe(STOCK_CODE);
    }
}
