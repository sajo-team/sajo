package com.sajo.market_service.strategy.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * strategy 패키지 소유의 Feign 클라이언트 전용 스캔 설정.
 * market.config.MarketClientConfiguration은 market.client.user만 스캔하므로(market 파트 코드는
 * 수정하지 않는다), strategy.client.user는 이 별도 설정으로 스캔한다.
 */
@Configuration
@EnableFeignClients(basePackages = "com.sajo.market_service.strategy.client.user")
public class StrategyClientConfiguration {
}
