package com.sajo.trading_service.trading.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.sajo.trading_service.trading.client")
public class TradingClientConfiguration {
}