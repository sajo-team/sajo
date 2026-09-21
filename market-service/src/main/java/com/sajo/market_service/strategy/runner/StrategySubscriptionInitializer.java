package com.sajo.market_service.strategy.runner;

import com.sajo.market_service.market.websocket.KisWebSocketClient;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 재기동 시 이미 활성화된 전략들의 종목을 WebSocket 구독 대상에 다시 추가한다.
 * {@link KisWebSocketClient#subscribe(String)}는 연결 전에 호출해도 안전하다(연결 성립 시
 * resubscribeAll이 전체 구독 목록을 재전송하므로
 * {@link com.sajo.market_service.market.runner.KisWebSocketRunner}와의 실행 순서는 무관하다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
public class StrategySubscriptionInitializer implements ApplicationRunner {

    private final StrategyQueryRepository strategyQueryRepository;
    private final KisWebSocketClient kisWebSocketClient;

    @Override
    public void run(ApplicationArguments args) {
        List<String> stockCodes =
                strategyQueryRepository.findDistinctStockCodesByStatusAndDeletedAtIsNull(StrategyStatus.ACTIVE);

        stockCodes.forEach(kisWebSocketClient::subscribe);

        log.info("활성 전략 종목을 WebSocket 구독 대상에 재등록했습니다. count={}", stockCodes.size());
    }
}
