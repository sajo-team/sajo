package com.sajo.market_service.market.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketWebSocketConfigurationTest {

    @Test
    void kisWebSocketTransportClientConfiguresTomcatIoTimeoutFromHandshakeTimeout() {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                false, "ws://localhost:31000", "11111111-1111-1111-1111-111111111111",
                Duration.ofMillis(10), Duration.ofSeconds(1), 2.0, List.of(), Duration.ofSeconds(7));

        StandardWebSocketClient client =
                (StandardWebSocketClient) new MarketWebSocketConfiguration().kisWebSocketTransportClient(properties);

        // orTimeout()은 애플리케이션 레벨에서 "기다리기를 포기"할 뿐 실제 핸드셰이크 소켓 I/O를 멈추지 못하므로,
        // Tomcat의 WsWebSocketContainer가 인식하는 IO_TIMEOUT_MS를 handshakeTimeout과 동일하게 설정해
        // 소켓 레벨에서도 확실히 타임아웃이 걸리는지 검증한다.
        assertThat(client.getUserProperties())
                .containsEntry("org.apache.tomcat.websocket.IO_TIMEOUT_MS", "7000");
    }
}
