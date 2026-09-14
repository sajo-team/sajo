package com.sajo.market_service.market.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties(MarketWebSocketProperties.class)
public class MarketWebSocketConfiguration {

    /**
     * market.websocket.enabled=false(계정 준비 전 기본값)인 로컬/CI/스테이징 환경에서까지
     * WebSocketClient/전용 데몬 스레드를 만들어 둘 필요가 없으므로, 실제로 연결을 시도하는
     * KisWebSocketRunner와 동일한 조건으로만 빈을 생성한다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
    WebSocketClient kisWebSocketTransportClient() {
        return new StandardWebSocketClient();
    }

    /** 재연결 예약 전용 단일 스레드 스케줄러. KIS 호출/구독 처리량은 크지 않아 하나로 충분하다. */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
    ScheduledExecutorService kisWebSocketReconnectScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kis-websocket-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }
}
