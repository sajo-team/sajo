package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sajo.scheduler")
public record MarketSchedulerProperties(
        boolean enabled,
        String systemUserId,
        String dailyPriceCron,
        int pageSize
) {

    public MarketSchedulerProperties {
        pageSize = pageSize > 0 ? pageSize : 100;
    }
}
