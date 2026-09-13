package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market.websocket")
public record MarketWebSocketProperties(
        boolean enabled,
        String url,
        String systemUserId,
        Duration initialBackoff,
        Duration maxBackoff,
        double backoffMultiplier
) {

    private static final String DEFAULT_URL = "ws://ops.koreainvestment.com:31000";
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(30);
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;

    public MarketWebSocketProperties {
        url = (url == null || url.isBlank()) ? DEFAULT_URL : url;
        initialBackoff = (initialBackoff == null || initialBackoff.isZero() || initialBackoff.isNegative())
                ? DEFAULT_INITIAL_BACKOFF
                : initialBackoff;
        maxBackoff = (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0)
                ? DEFAULT_MAX_BACKOFF
                : maxBackoff;
        backoffMultiplier = backoffMultiplier > 1.0 ? backoffMultiplier : DEFAULT_BACKOFF_MULTIPLIER;
    }
}
