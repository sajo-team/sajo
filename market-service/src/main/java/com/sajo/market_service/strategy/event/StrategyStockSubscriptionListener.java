package com.sajo.market_service.strategy.event;

import com.sajo.market_service.market.config.MarketWebSocketProperties;
import com.sajo.market_service.market.websocket.KisWebSocketClient;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 전략 활성화/비활성화가 커밋된 뒤 WebSocket 구독 상태와 Signal 반복-방지 상태를 갱신한다.
// KisWebSocketClient.unsubscribe()는 참조 카운트 없는 전역 해제이므로, 같은 종목을 쓰는 다른
// 활성 전략이 남아있는지와 정적 구독 대상(target-stock-codes) 여부를 여기서 직접 판단한다.

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
public class StrategyStockSubscriptionListener {

    private final KisWebSocketClient kisWebSocketClient;
    private final StrategyEvaluationService strategyEvaluationService;
    private final StrategyQueryRepository strategyQueryRepository;
    private final MarketWebSocketProperties marketWebSocketProperties;

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
        unsubscribeIfNoLongerNeeded(event.stockCode());
    }

    private void unsubscribeIfNoLongerNeeded(String stockCode) {
        if (strategyQueryRepository.existsByStockCodeAndStatusAndDeletedAtIsNull(stockCode, StrategyStatus.ACTIVE)) {
            log.info("같은 종목을 쓰는 다른 활성 전략이 남아있어 구독을 유지합니다. stockCode={}", stockCode);
            return;
        }

        if (marketWebSocketProperties.targetStockCodes().contains(stockCode)) {
            log.info("정적으로 설정된 구독 대상 종목이라 구독을 해제하지 않습니다. stockCode={}", stockCode);
            return;
        }

        kisWebSocketClient.unsubscribe(stockCode);
        log.info("전략 비활성화로 더 이상 필요하지 않은 종목의 WebSocket 구독을 해제했습니다. stockCode={}", stockCode);
    }
}
