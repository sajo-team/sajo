package com.sajo.market_service.strategy.runner;

import com.sajo.market_service.market.websocket.KisWebSocketClient;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StrategySubscriptionInitializerTest {

    @Mock
    private StrategyQueryRepository strategyQueryRepository;

    @Mock
    private KisWebSocketClient kisWebSocketClient;

    @Test
    @DisplayName("기동 시 활성 전략 종목을 모두 재구독한다")
    void resubscribesAllActiveStrategyStockCodesOnStartup() {
        given(strategyQueryRepository.findDistinctStockCodesByStatusAndDeletedAtIsNull(StrategyStatus.ACTIVE))
                .willReturn(List.of("005930", "000660"));

        StrategySubscriptionInitializer initializer =
                new StrategySubscriptionInitializer(strategyQueryRepository, kisWebSocketClient);
        initializer.run(null);

        InOrder inOrder = inOrder(kisWebSocketClient);
        inOrder.verify(kisWebSocketClient).subscribe("005930");
        inOrder.verify(kisWebSocketClient).subscribe("000660");
    }

    @Test
    @DisplayName("활성 전략이 없으면 구독을 요청하지 않는다")
    void doesNothingWhenNoActiveStrategies() {
        given(strategyQueryRepository.findDistinctStockCodesByStatusAndDeletedAtIsNull(StrategyStatus.ACTIVE))
                .willReturn(List.of());

        StrategySubscriptionInitializer initializer =
                new StrategySubscriptionInitializer(strategyQueryRepository, kisWebSocketClient);
        initializer.run(null);

        verify(kisWebSocketClient, never()).subscribe(org.mockito.ArgumentMatchers.anyString());
    }
}
