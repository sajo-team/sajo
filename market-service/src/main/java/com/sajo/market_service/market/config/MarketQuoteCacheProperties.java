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
        // 코드리뷰에서 지적된 대로, ttl(시세 캐시 TTL)은 실시간 체결마다 갱신되어 장중에는 사실상
        // 만료되지 않는데 이 마커만 짧게 만료되면 마커 재확인 주기마다 KIS REST가 반복 호출된다.
        // 그래서 ttl보다 충분히 길게(기본 5분) 잡아 장중 반복 호출을 사실상 없애면서도, 값이 새로
        // 생기면(예: 다음 거래일) 무한정 캐싱되지 않고 재확인되도록 한다.
        previousClosePriceMissingTtl = validOrDefault(previousClosePriceMissingTtl, Duration.ofMinutes(5));
    }

    private static Duration validOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
