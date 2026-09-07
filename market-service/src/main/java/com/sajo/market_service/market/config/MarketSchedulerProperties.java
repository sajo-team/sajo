package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "sajo.scheduler")
public record MarketSchedulerProperties(
        boolean enabled,
        String systemUserId,
        String dailyPriceCron,
        int pageSize,
        boolean indicatorEnabled,
        String indicatorCron,
        Duration kisRequestInterval
) {

    public MarketSchedulerProperties {
        pageSize = pageSize > 0 ? pageSize : 100;
        Duration minimumIndicatorInterval = Duration.ofMillis(500);
        kisRequestInterval = kisRequestInterval == null
                || kisRequestInterval.compareTo(minimumIndicatorInterval) < 0
                ? minimumIndicatorInterval
                : kisRequestInterval;
    }

    public MarketSchedulerProperties(boolean enabled, String systemUserId, String dailyPriceCron, int pageSize) {
        this(enabled, systemUserId, dailyPriceCron, pageSize, false, "0 20 16 * * MON-FRI", Duration.ofMillis(500));
    }
}
