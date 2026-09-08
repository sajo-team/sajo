package com.sajo.user_service.account.client.feign;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.sajo.user_service.account.client.feign")
public class TradingClientConfiguration {
}
