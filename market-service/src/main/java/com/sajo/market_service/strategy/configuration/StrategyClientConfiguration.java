package com.sajo.market_service.strategy.configuration;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.sajo.market_service.strategy.client")
public class StrategyClientConfiguration {
}
