package com.sajo.market_service.market.runner;

import com.sajo.market_service.market.websocket.KisWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
public class KisWebSocketRunner implements ApplicationRunner {

    private final KisWebSocketClient kisWebSocketClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("KIS WebSocket 연결을 시작합니다.");
        kisWebSocketClient.connect();
    }
}
