package com.sajo.market_service.market.websocket;

import com.sajo.market_service.market.config.MarketWebSocketProperties;

import java.time.Duration;

/**
 * 재연결 시도 횟수(attempt, 0부터 시작)에 따라 다음 재연결까지 대기할 시간을 계산하는 지수 백오프 정책이다.
 *
 * <p>연결이 끊길 때마다 {@code initialBackoff * multiplier^attempt}만큼 대기 시간이 늘어나고,
 * {@code maxBackoff}를 넘지 않는다. 순수 계산 로직만 담당하며 실제 대기·재연결 실행은 호출자가 수행한다.</p>
 */
public class KisWebSocketReconnectPolicy {

    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double backoffMultiplier;

    public KisWebSocketReconnectPolicy(MarketWebSocketProperties properties) {
        this(properties.initialBackoff(), properties.maxBackoff(), properties.backoffMultiplier());
    }

    KisWebSocketReconnectPolicy(Duration initialBackoff, Duration maxBackoff, double backoffMultiplier) {
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * @param attempt 이번이 몇 번째 연속 재연결 시도인지 (첫 번째 재연결 시도는 0)
     * @return 이번 재연결 시도 전 대기해야 할 시간
     */
    public Duration nextDelay(int attempt) {
        if (attempt <= 0) {
            return capAtMax(initialBackoff);
        }
        double multipliedNanos = initialBackoff.toNanos() * Math.pow(backoffMultiplier, attempt);
        if (multipliedNanos >= maxBackoff.toNanos() || Double.isInfinite(multipliedNanos)) {
            return maxBackoff;
        }
        return capAtMax(Duration.ofNanos((long) multipliedNanos));
    }

    private Duration capAtMax(Duration candidate) {
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }
}
