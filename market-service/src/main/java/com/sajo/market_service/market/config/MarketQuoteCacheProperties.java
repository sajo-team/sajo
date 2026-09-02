package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market.quote-cache")
public record MarketQuoteCacheProperties(
        Duration ttl,
        Duration lockTtl,
        Duration lockWaitTimeout
) {

    public MarketQuoteCacheProperties {
        ttl = validOrDefault(ttl, Duration.ofSeconds(60));
        lockTtl = validOrDefault(lockTtl, Duration.ofSeconds(30));
        lockWaitTimeout = validOrDefault(lockWaitTimeout, Duration.ofSeconds(5));
    }

    private static Duration validOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
