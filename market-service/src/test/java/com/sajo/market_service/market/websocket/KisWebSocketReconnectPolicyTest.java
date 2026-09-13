package com.sajo.market_service.market.websocket;

import com.sajo.market_service.market.config.MarketWebSocketProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KisWebSocketReconnectPolicyTest {

    @Test
    void firstAttemptUsesInitialBackoff() {
        KisWebSocketReconnectPolicy policy = policy(Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);

        assertThat(policy.nextDelay(0)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void delayGrowsExponentiallyWithEachAttempt() {
        KisWebSocketReconnectPolicy policy = policy(Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);

        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.nextDelay(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void delayNeverExceedsMaxBackoff() {
        KisWebSocketReconnectPolicy policy = policy(Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0);

        assertThat(policy.nextDelay(10)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.nextDelay(100)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void negativeAttemptIsTreatedAsFirstAttempt() {
        KisWebSocketReconnectPolicy policy = policy(Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);

        assertThat(policy.nextDelay(-1)).isEqualTo(Duration.ofSeconds(1));
    }

    private KisWebSocketReconnectPolicy policy(Duration initial, Duration max, double multiplier) {
        MarketWebSocketProperties properties =
                new MarketWebSocketProperties(true, "ws://localhost", "", initial, max, multiplier);
        return new KisWebSocketReconnectPolicy(properties);
    }
}
