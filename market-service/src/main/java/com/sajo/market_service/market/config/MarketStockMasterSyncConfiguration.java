package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarketStockMasterSyncProperties.class)
public class MarketStockMasterSyncConfiguration {
}
