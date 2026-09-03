package com.sajo.trading_service.ai_risk.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.sajo.trading_service.ai_risk.client")
public class AiClientConfiguration {
}
