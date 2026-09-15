package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market.quote-cache")
public record MarketQuoteCacheProperties(
        Duration ttl,
        Duration lockTtl,
        Duration lockWaitTimeout,
        Duration previousClosePriceMissingTtl
) {

    public MarketQuoteCacheProperties {
        ttl = validOrDefault(ttl, Duration.ofSeconds(60));
        lockTtl = validOrDefault(lockTtl, Duration.ofSeconds(30));
        lockWaitTimeout = validOrDefault(lockWaitTimeout, Duration.ofSeconds(5));
        // KIS REST 응답 자체에 previousClosePrice가 없다고 확인된 종목을 얼마나 기억해둘지(#228)
        // ttl보다 짧게 잡아, 값이 새로 생기면(예: 다음 거래일) 비교적 빨리 재확인한다.
        previousClosePriceMissingTtl = validOrDefault(previousClosePriceMissingTtl, Duration.ofSeconds(15));
    }

    private static Duration validOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
