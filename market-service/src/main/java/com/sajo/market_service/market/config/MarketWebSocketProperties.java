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
        // maxBackoff가 없으면 기본값(30s)을 쓰되, initialBackoff보다 작아지지 않도록 initialBackoff로 올려 잡는다.
        // (예: initialBackoff를 30s보다 크게 설정하고 maxBackoff를 지정하지 않은 경우, 기본값 30s로
        // 되돌리면 운영자 의도와 다르게 초기값보다 짧은 상한이 조용히 적용될 수 있다.)
        Duration resolvedMaxBackoff = (maxBackoff == null) ? DEFAULT_MAX_BACKOFF : maxBackoff;
        maxBackoff = resolvedMaxBackoff.compareTo(initialBackoff) < 0 ? initialBackoff : resolvedMaxBackoff;
        backoffMultiplier = backoffMultiplier > 1.0 ? backoffMultiplier : DEFAULT_BACKOFF_MULTIPLIER;
    }
}
