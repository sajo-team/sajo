package com.sajo.market_service.strategy.event;

import com.sajo.market_service.market.websocket.KisWebSocketClient;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


// 전략 활성화/비활성화가 커밋된 뒤 WebSocket 구독 상태와 Signal 반복-방지 상태를 갱신한다.
// 구독 해제(unsubscribe)는 {@link KisWebSocketClient}에 아직 없어(market 담당 후속 작업)
// 비활성화 시에는 반복-방지 상태 초기화만 수행한다.

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
public class StrategyStockSubscriptionListener {

    private final KisWebSocketClient kisWebSocketClient;
    private final StrategyEvaluationService strategyEvaluationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(StrategyActivationChangedEvent event) {
        if (event.active()) {
            kisWebSocketClient.subscribe(event.stockCode());
            log.info("전략 활성화에 따라 WebSocket 구독을 요청했습니다. strategyId={}, stockCode={}",
                    event.strategyId(), event.stockCode());
            return;
        }

        strategyEvaluationService.clearSignalState(event.strategyId());
        log.info("전략 비활성화로 Signal 반복-방지 상태를 초기화했습니다. strategyId={}", event.strategyId());
    }
}
