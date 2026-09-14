package com.sajo.market_service.market.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MarketWebSocketPropertiesTest {

    @Test
    void appliesDefaultsWhenNothingIsConfigured() {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                false, null, "", null, null, 0);

        assertThat(properties.url()).isEqualTo("ws://ops.koreainvestment.com:31000");
        assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.maxBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.backoffMultiplier()).isEqualTo(2.0);
    }

    @Test
    void keepsExplicitMaxBackoffWhenNotSmallerThanInitialBackoff() {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                true, "ws://kis.example", "", Duration.ofSeconds(2), Duration.ofSeconds(20), 3.0);

        assertThat(properties.maxBackoff()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void unconfiguredMaxBackoffFallsBackToDefaultWhenInitialBackoffIsSmaller() {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                true, "ws://kis.example", "", Duration.ofSeconds(5), null, 2.0);

        assertThat(properties.maxBackoff()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void maxBackoffNeverEndsUpSmallerThanInitialBackoffEvenWhenExplicitlyConfiguredTooSmall() {
        // initialBackoff(40s)가 기본 maxBackoff(30s)보다 큰 상태에서 maxBackoff를 지정하지 않으면
        // 초기값보다 짧은 상한이 조용히 적용되지 않도록 initialBackoff로 올려 잡아야 한다.
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                true, "ws://kis.example", "", Duration.ofSeconds(40), null, 2.0);

        assertThat(properties.maxBackoff()).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void explicitMaxBackoffSmallerThanInitialBackoffIsClampedUpToInitialBackoff() {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                true, "ws://kis.example", "", Duration.ofSeconds(10), Duration.ofSeconds(3), 2.0);

        assertThat(properties.maxBackoff()).isEqualTo(Duration.ofSeconds(10));
    }
}
