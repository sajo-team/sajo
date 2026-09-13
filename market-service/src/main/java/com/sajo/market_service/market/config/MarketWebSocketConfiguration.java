package com.sajo.market_service.market.config;

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

    @Bean
    WebSocketClient kisWebSocketTransportClient() {
        return new StandardWebSocketClient();
    }

    /** 재연결 예약 전용 단일 스레드 스케줄러. KIS 호출/구독 처리량은 크지 않아 하나로 충분하다. */
    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService kisWebSocketReconnectScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kis-websocket-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }
}
